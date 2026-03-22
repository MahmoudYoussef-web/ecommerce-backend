package com.mahmoud.ecommerce_backend.dto.order;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressSnapshot {
    private String country;
    private String city;
    private String street;
    private String zipCode;
}