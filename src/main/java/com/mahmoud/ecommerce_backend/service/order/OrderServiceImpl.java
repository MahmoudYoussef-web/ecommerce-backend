package com.mahmoud.ecommerce_backend.service.order;

import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.dto.order.OrderResponse;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.mapper.OrderMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }

        if (request.getAddressId() == null) {
            throw new BadRequestException("AddressId must not be null");
        }

        User user = getCurrentUser();
        log.info("Creating order for userId={}", user.getId());

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        if (cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You are not allowed to use this address");
        }

        Order order = Order.builder()
                .user(user)
                .orderNumber(UUID.randomUUID().toString())
                .shippingAddress(AddressSnapshot.from(address))
                .shippingCost(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .customerNotes(request.getCustomerNotes())
                .build();

        int itemCount = 0;

        for (CartItem item : cart.getCartItems()) {

            Product product = item.getProduct();

            if (product == null) {
                log.warn("Cart item has null product");
                throw new BadRequestException("Invalid cart item");
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                log.warn("Invalid quantity for productId={}", product.getId());
                throw new BadRequestException("Invalid quantity for product: " + product.getName());
            }

            if (product.getStatus() == ProductStatus.DRAFT) {
                throw new BadRequestException("Product is not available for purchase: " + product.getName());
            }

            if (product.getStockQuantity() < item.getQuantity()) {
                log.warn("Insufficient stock for productId={}, requested={}, available={}",
                        product.getId(), item.getQuantity(), product.getStockQuantity());

                throw new BadRequestException("Insufficient stock for product: " + product.getName());
            }

            int newStock = product.getStockQuantity() - item.getQuantity();

            if (newStock < 0) {
                log.error("Stock would become negative for productId={}", product.getId());
                throw new BadRequestException("Stock inconsistency detected");
            }

            if (item.getUnitPrice() != null &&
                    item.getUnitPrice().compareTo(product.getEffectivePrice()) != 0) {

                log.warn("Price mismatch for productId={}, cartPrice={}, currentPrice={}",
                        product.getId(), item.getUnitPrice(), product.getEffectivePrice());
            }

            product.setStockQuantity(newStock);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .productImageUrl(null)
                    .priceAtPurchase(product.getEffectivePrice())
                    .quantity(item.getQuantity())
                    .order(order)
                    .build();

            order.addItem(orderItem);
            itemCount++;
        }

        if (order.getOrderItems().isEmpty()) {
            throw new BadRequestException("Cannot create order with no items");
        }

        orderRepository.save(order);
        cart.getCartItems().clear();

        log.info("Order created: orderNumber={}, items={}, subtotal={}, total={}",
                order.getOrderNumber(),
                itemCount,
                order.getSubtotal(),
                order.getTotalAmount());

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
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You are not allowed to access this order");
        }

        return orderMapper.toResponse(order);
    }

    private User getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenException("Unauthenticated access");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}