package com.mahmoud.ecommerce_backend.service.admin;

import com.mahmoud.ecommerce_backend.dto.order.AddressSnapshot;
import com.mahmoud.ecommerce_backend.dto.order.AdminOrderSummaryResponse;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.mapper.OrderMapper;
import com.mahmoud.ecommerce_backend.mapper.UserMapper;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final OrderRepository orderRepository;
    private final UserRoleRepository userRoleRepository;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;

    @Override
    public List<AdminOrderSummaryResponse> getAllOrders() {

        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<UserResponse> getCustomers() {

        List<User> customers = userRoleRepository.findUsersByRole(RoleName.ROLE_CUSTOMER);

        return customers.stream()
                .map(user -> {
                    UserResponse response = userMapper.toResponse(user);
                    response.setRoles(List.of(RoleName.ROLE_CUSTOMER.name()));
                    return response;
                })
                .toList();
    }

    private AdminOrderSummaryResponse toSummary(Order order) {

        User user = order.getUser();

        AddressSnapshot address = order.getShippingAddress() == null
                ? null
                : orderMapper.map(order.getShippingAddress());

        return AdminOrderSummaryResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .createdAt(order.getCreatedAt())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .totalAmount(order.getTotalAmount())
                .customerName(user != null ? user.getFullName() : null)
                .customerEmail(user != null ? user.getEmail() : null)
                .itemsCount(order.getOrderItems().size())
                .address(address)
                .paymentStatus(order.getPayment() != null && order.getPayment().getStatus() != null
                        ? order.getPayment().getStatus().name()
                        : null)
                .build();
    }
}
