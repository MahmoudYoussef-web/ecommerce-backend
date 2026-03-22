package com.mahmoud.ecommerce_backend.dto.address;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressRequest {
    private String country;
    private String city;
    private String street;
    private String zipCode;
}