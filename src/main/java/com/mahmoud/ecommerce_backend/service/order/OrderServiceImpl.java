package com.mahmoud.ecommerce_backend.service.order;

import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.dto.order.OrderResponse;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.event.inventory.OrderCreatedEvent;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.OrderMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import com.mahmoud.ecommerce_backend.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final OrderMapper orderMapper;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationService reservationService;

    private static final int MAX_RETRIES = 3;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return processOrder(request);
            } catch (OptimisticLockingFailureException ex) {
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
    public OrderResponse getOrderById(Long id) {

        User user = securityService.getCurrentUser();
        Order order = findOrderOrThrow(id);

          validateOwnership(order, user);

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public void markAsShipped(Long id) {
        Order order = findOrderOrThrow(id);


        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order must be PENDING to ship");
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

        reservationService.release(order.getId());

        order.markAsCancelled("Cancelled");
    }



    private OrderResponse processOrder(CreateOrderRequest request) {

        User user = securityService.getCurrentUser();
        Cart cart = getCart(user.getId());
        Address address = getAddress(request.getAddressId(), user.getId());

        Order order = buildOrder(user, address, request);

        buildOrderItems(order, cart);


        reserveInventory(order);

        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCreatedEvent(this, order));

        cart.getCartItems().clear();

        return orderMapper.toResponse(order);
    }



    private void buildOrderItems(Order order, Cart cart) {

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