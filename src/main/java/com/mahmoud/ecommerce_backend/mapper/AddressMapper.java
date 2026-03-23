package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.address.AddressResponse;
import com.mahmoud.ecommerce_backend.entity.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    @Mapping(target = "street", source = "addressLine1")
    @Mapping(target = "zipCode", source = "postalCode")
    AddressResponse toResponse(Address address);
}