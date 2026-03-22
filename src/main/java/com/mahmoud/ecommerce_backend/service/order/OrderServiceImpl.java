package com.mahmoud.ecommerce_backend.service.order;


import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.dto.order.OrderResponse;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.OrderMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.service.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final OrderMapper orderMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart is empty"));

        if (cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }
        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        Order order = Order.builder()
                .user(user)
                .orderNumber(UUID.randomUUID().toString())
                .shippingAddress(AddressSnapshot.from(address))
                .build();

        for (CartItem item : cart.getCartItems()) {
            OrderItem orderItem = OrderItem.builder()
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .productSku(item.getProduct().getSku())
                    .productImageUrl(null)
                    .priceAtPurchase(item.getUnitPrice())
                    .quantity(item.getQuantity())
                    .order(order)
                    .build();

            order.addItem(orderItem);
        }

        orderRepository.save(order);

        cart.getCartItems().clear();

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

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
    private OrderItem mapFromCartItem(CartItem item) {
        return OrderItem.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productSku(item.getProduct().getSku())
                .productImageUrl(null)
                .priceAtPurchase(item.getUnitPrice())
                .quantity(item.getQuantity())
                .build();
    }
}