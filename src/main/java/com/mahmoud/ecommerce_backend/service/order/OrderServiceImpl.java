package com.mahmoud.ecommerce_backend.service.order;

import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.dto.order.OrderResponse;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.event.inventory.OrderCreatedEvent;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.OrderMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.service.coupon.CouponService;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import com.mahmoud.ecommerce_backend.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationService reservationService;
    private final TransactionTemplate transactionTemplate;
    private final CouponService couponService;

    @Value("${app.currency.egp-per-usd}")
    private BigDecimal egpPerUsd;

    private static final int MAX_RETRIES = 3;

    /**
     * NOT @Transactional: each retry attempt must run in a FRESH transaction.
     * A previous implementation retried inside one transactional method —
     * after an OptimisticLockingFailureException the transaction was already
     * rollback-only, so retries were futile and the commit failed.
     */
    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(tx -> processOrder(request));
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Concurrent update while placing order (attempt {}/{})", attempt, MAX_RETRIES);
                if (attempt == MAX_RETRIES) {
                    throw new BadRequestException("Concurrent update detected");
                }
            }
        }
        throw new BadRequestException("Order failed");
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders() {
        User user = securityService.getCurrentUser();
        return orderRepository.findByUserId(user.getId())
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(Pageable pageable) {

        User user = securityService.getCurrentUser();

        Page<Order> page = orderRepository.findByUserId(user.getId(), pageable);

        if (page.isEmpty()) {
            return Page.empty(pageable);
        }

        // Batched item load for the whole page — the lazy per-order collection
        // is never touched, keeping query count flat as history grows.
        List<Long> orderIds = page.getContent().stream().map(Order::getId).toList();

        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds)
                .stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        List<OrderResponse> content = page.getContent().stream()
                .map(order -> {
                    OrderResponse response = orderMapper.toResponseShallow(order);
                    response.setItems(orderMapper.toItemResponses(
                            itemsByOrderId.getOrDefault(order.getId(), List.of())));
                    return response;
                })
                .toList();

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {

        User user = securityService.getCurrentUser();
        Order order = findOrderOrThrow(id);

        if (!isStaff(user)) {
            validateOwnership(order, user);
        }

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public void markAsShipped(Long id) {
        Order order = findOrderOrThrow(id);


        if (order.getStatus() != OrderStatus.PAID) {
            throw new BadRequestException("Order must be PAID to ship");
        }

        order.markAsShipped("SYSTEM", null);
    }

    @Override
    @Transactional
    public void markAsDelivered(Long id) {
        Order order = findOrderOrThrow(id);

        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BadRequestException("Order must be SHIPPED to deliver");
        }

        order.markAsDelivered();
    }


    @Override
    @Transactional
    public void cancelOrder(Long id) {

        Order order = findOrderOrThrow(id);

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Cannot cancel delivered order");
        }

        reservationService.releaseForOrder(order.getId());

        order.markAsCancelled("Cancelled");
    }

    @Override
    @Transactional
    public void requestReturn(Long id, String reason) {
        User user = securityService.getCurrentUser();
        Order order = findOrderOrThrow(id);
        validateOwnership(order, user);
        try {
            order.requestReturn(reason);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    @Override
    @Transactional
    public void approveReturn(Long id) {
        Order order = findOrderOrThrow(id);
        try {
            order.approveReturn();
        } catch (IllegalStateException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }



    private OrderResponse processOrder(CreateOrderRequest request) {

        User user = securityService.getCurrentUser();
        Cart cart = getCart(user.getId());
        Address address = getAddress(request.getAddressId(), user.getId());

        Order order = buildOrder(user, address, request);

        buildOrderItems(order, cart);

        applyCouponIfPresent(order, request);

        snapshotCurrency(order);

        orderRepository.save(order);

        reserveInventory(order);

        eventPublisher.publishEvent(new OrderCreatedEvent(this, order));

        if (request.getPaymentMethod() != PaymentMethod.STRIPE) {
            cart.getCartItems().clear();
        }

        return orderMapper.toResponse(order);
    }



    private void buildOrderItems(Order order, Cart cart) {

        if (cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        for (CartItem cartItem : cart.getCartItems()) {

            Product product = getProduct(cartItem.getProduct().getId());

            validateProduct(product);
            validateQuantity(cartItem.getQuantity());

            BigDecimal price;
            Long variantId = null;

            if (cartItem.getVariant() != null) {

                ProductVariant variant = getVariant(cartItem.getVariant().getId());

                if (variant.getStockQuantity() < cartItem.getQuantity()) {
                    throw new BadRequestException("Insufficient variant stock");
                }

                price = variant.getEffectivePrice(product);
                variantId = variant.getId();

            } else {

                if (product.getStockQuantity() < cartItem.getQuantity()) {
                    throw new BadRequestException("Insufficient stock");
                }

                price = product.getEffectivePrice();
            }

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .variantId(variantId)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .priceAtPurchase(price)
                    .quantity(cartItem.getQuantity())
                    .order(order)
                    .build();

            order.addItem(item);
        }
    }



    private void reserveInventory(Order order) {

        for (OrderItem item : order.getOrderItems()) {

            reservationService.reserve(
                    item.getProductId(),
                    item.getQuantity(),
                    order.getId()
            );
        }
    }



    private Cart getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
    }

    private Address getAddress(Long addressId, Long userId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!Objects.equals(address.getUser().getId(), userId)) {
            throw new ForbiddenException("Forbidden");
        }

        return address;
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private ProductVariant getVariant(Long id) {
        return productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
    }

    private void validateProduct(Product product) {
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BadRequestException("Product not available");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Invalid quantity");
        }
    }

    private void validateOwnership(Order order, User user) {
        if (!Objects.equals(order.getUser().getId(), user.getId())) {
            throw new ForbiddenException("Forbidden");
        }
    }

    private boolean isStaff(User user) {
        return user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .anyMatch(name -> name == RoleName.ROLE_ADMIN || name == RoleName.ROLE_WAREHOUSE);
    }

    private void applyCouponIfPresent(Order order, CreateOrderRequest request) {
        if (request.getCouponCode() == null || request.getCouponCode().isBlank()) {
            return;
        }
        String code = request.getCouponCode().trim().toUpperCase();
        BigDecimal discount = couponService.applyCoupon(code, order.getSubtotal());
        order.setCouponCode(code);
        order.applyDiscount(discount);
    }

    private void snapshotCurrency(Order order) {        BigDecimal rate = (egpPerUsd != null && egpPerUsd.compareTo(BigDecimal.ZERO) > 0)
                ? egpPerUsd
                : BigDecimal.ONE;

        BigDecimal totalEgp = order.getTotalAmount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        order.setCurrencySnapshot(totalEgp, rate, Instant.now());
    }

    private Order buildOrder(User user, Address address, CreateOrderRequest request) {
        Order order = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .shippingAddress(AddressSnapshot.from(address))
                .status(OrderStatus.PENDING)
                .customerNotes(request.getCustomerNotes())
                .build();

        order.assignUser(user);
        return order;
    }
}