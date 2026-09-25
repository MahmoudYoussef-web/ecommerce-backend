package com.mahmoud.ecommerce_backend.service.admin;

import com.mahmoud.ecommerce_backend.dto.order.AddressSnapshot;
import com.mahmoud.ecommerce_backend.dto.order.AdminOrderSummaryResponse;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.mapper.OrderMapper;
import com.mahmoud.ecommerce_backend.mapper.UserMapper;
import com.mahmoud.ecommerce_backend.repository.OrderItemRepository;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import com.mahmoud.ecommerce_backend.repository.UserRoleRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final UserRoleRepository userRoleRepository;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;

    @Override
    public Page<AdminOrderSummaryResponse> getOrders(Pageable pageable, OrderStatus status, String search) {

        Specification<Order> spec = buildOrderSpecification(status, search);

        Page<Order> page = orderRepository.findAll(spec, pageable);

        List<Order> orders = page.getContent();
        if (orders.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> ids = orders.stream().map(Order::getId).toList();

        // One batched query per association class — never per row.
        Map<Long, User> usersById = orderRepository.findAllWithUserByIdIn(ids).stream()
                .collect(Collectors.toMap(Order::getId, o -> o.getUser(), (a, b) -> a));
        Map<Long, Integer> itemCounts = loadItemCounts(ids);
        Map<Long, PaymentStatus> paymentStatuses = loadPaymentStatuses(ids);

        List<AdminOrderSummaryResponse> content = orders.stream()
                .map(order -> toSummary(
                        order,
                        usersById.get(order.getId()),
                        itemCounts.getOrDefault(order.getId(), 0),
                        paymentStatuses.get(order.getId())))
                .toList();

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    public List<AdminOrderSummaryResponse> exportOrders(OrderStatus status, String search) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0, 5000, Sort.by(Sort.Direction.DESC, "createdAt"));
        return getOrders(pageable, status, search).getContent();
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

    @Override
    public Page<UserResponse> getCustomers(Pageable pageable) {

        Page<User> page = userRoleRepository.findUsersByRole(RoleName.ROLE_CUSTOMER, pageable);

        List<UserResponse> content = page.getContent().stream()
                .map(user -> {
                    UserResponse response = userMapper.toResponse(user);
                    response.setRoles(List.of(RoleName.ROLE_CUSTOMER.name()));
                    return response;
                })
                .toList();

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /**
     * Dynamic filter for the admin orders table: optional status and a free-text
     * search across order number, customer name and customer email.
     *
     * Note: no lower() wrappers — the MySQL column collation is already
     * case-insensitive (utf8mb4_0900_ai_ci), and function-wrapped predicates
     * defeat index usage entirely.
     */
    private Specification<Order> buildOrderSpecification(OrderStatus status, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim() + "%";
                var user = root.join("user", jakarta.persistence.criteria.JoinType.LEFT);
                Predicate byNumber = cb.like(root.get("orderNumber"), like);
                Predicate byFirstName = cb.like(user.get("firstName"), like);
                Predicate byLastName = cb.like(user.get("lastName"), like);
                Predicate byEmail = cb.like(user.get("email"), like);
                predicates.add(cb.or(byNumber, byFirstName, byLastName, byEmail));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Single grouped query for item counts — avoids N+1 lazy loads per row. */
    private Map<Long, Integer> loadItemCounts(List<Long> ids) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : orderItemRepository.countByOrderIds(ids)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    /** Single grouped query for payment statuses — replaces per-row inverse-OneToOne access. */
    private Map<Long, PaymentStatus> loadPaymentStatuses(List<Long> ids) {
        Map<Long, PaymentStatus> statuses = new HashMap<>();
        for (Object[] row : paymentRepository.findStatusByOrderIds(ids)) {
            statuses.put((Long) row[0], (PaymentStatus) row[1]);
        }
        return statuses;
    }

    private AdminOrderSummaryResponse toSummary(Order order,
                                                User user,
                                                int itemsCount,
                                                PaymentStatus paymentStatus) {

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
                .itemsCount(itemsCount)
                .address(address)
                .paymentStatus(paymentStatus != null ? paymentStatus.name() : null)
                .build();
    }
}
