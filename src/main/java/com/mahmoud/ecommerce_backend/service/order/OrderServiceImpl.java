package com.mahmoud.ecommerce_backend.service.order;

import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.dto.order.OrderResponse;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.OrderMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final OrderMapper orderMapper;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    private static final int MAX_RETRIES = 3;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return processOrder(request);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Retrying order creation attempt={}", attempt + 1);
                if (attempt == MAX_RETRIES - 1) {
                    throw new BadRequestException("Failed due to concurrent updates, retry again");
                }
            }
        }

        throw new BadRequestException("Order processing failed");
    }

    @Transactional
    protected OrderResponse processOrder(CreateOrderRequest request) {

        if (request == null || request.getAddressId() == null) {
            throw new BadRequestException("Invalid request");
        }

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        if (cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Forbidden address");
        }

        Order order = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .shippingAddress(AddressSnapshot.from(address))
                .shippingCost(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .customerNotes(request.getCustomerNotes())
                .status(OrderStatus.PENDING)
                .build();

        order.assignUser(user);

        for (CartItem item : cart.getCartItems()) {

            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            ProductVariant variant = null;

            if (item.getVariant() != null) {
                variant = productVariantRepository.findById(item.getVariant().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Invalid quantity");
            }

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BadRequestException("Product not available");
            }

            BigDecimal price;
            Long variantId = null;

            if (variant != null) {

                if (!variant.getProduct().getId().equals(product.getId())) {
                    throw new BadRequestException("Invalid variant");
                }

                if (variant.getStockQuantity() < item.getQuantity()) {
                    throw new BadRequestException("Insufficient variant stock");
                }

                variant.decreaseStock(item.getQuantity());
                price = variant.getEffectivePrice(product);
                variantId = variant.getId();

            } else {

                if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                    throw new BadRequestException("Variant required");
                }

                if (product.getStockQuantity() < item.getQuantity()) {
                    throw new BadRequestException("Insufficient stock");
                }

                product.decreaseStock(item.getQuantity());
                price = product.getEffectivePrice();
            }

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .variantId(variantId)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .priceAtPurchase(price)
                    .quantity(item.getQuantity())
                    .order(order)
                    .build();

            order.addItem(orderItem);
        }

        if (order.getOrderItems().isEmpty()) {
            throw new BadRequestException("Empty order");
        }

        orderRepository.save(order);

        cart.getCartItems().clear();
        cart.recalculateTotal();

        return orderMapper.toResponse(order);
    }

    @Override
    public List<OrderResponse> getUserOrders() {
        User user = getCurrentUser();

        return orderRepository.findByUserId(user.getId())
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    public OrderResponse getOrderById(Long id) {

        User user = getCurrentUser();

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Forbidden");
        }

        return orderMapper.toResponse(order);
    }

    private User getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenException("Unauthenticated");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}