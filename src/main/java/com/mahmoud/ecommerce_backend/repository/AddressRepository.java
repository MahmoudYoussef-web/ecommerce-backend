package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserId(Long userId);
}
