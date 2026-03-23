package com.mahmoud.ecommerce_backend.service.address;

import com.mahmoud.ecommerce_backend.dto.address.AddressResponse;
import com.mahmoud.ecommerce_backend.dto.address.CreateAddressRequest;

import java.util.List;

public interface AddressService {

    AddressResponse createAddress(CreateAddressRequest request);

    List<AddressResponse> getUserAddresses();

    void deleteAddress(Long id);
}