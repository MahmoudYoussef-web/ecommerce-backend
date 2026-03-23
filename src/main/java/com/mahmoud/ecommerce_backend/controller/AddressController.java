package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.address.AddressResponse;
import com.mahmoud.ecommerce_backend.dto.address.CreateAddressRequest;
import com.mahmoud.ecommerce_backend.service.address.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public AddressResponse create(@Valid @RequestBody CreateAddressRequest request) {
        return addressService.createAddress(request);
    }

    @GetMapping
    public List<AddressResponse> getUserAddresses() {
        return addressService.getUserAddresses();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        addressService.deleteAddress(id);
    }
}