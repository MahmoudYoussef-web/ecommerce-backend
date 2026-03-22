package com.mahmoud.ecommerce_backend.dto.address;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    private Long id;
    private String country;
    private String city;
    private String street;
    private String zipCode;
}