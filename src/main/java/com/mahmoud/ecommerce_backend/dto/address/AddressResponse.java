package com.mahmoud.ecommerce_backend.dto.address;

import com.mahmoud.ecommerce_backend.enums.AddressType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    private Long id;
    private String fullName;
    private String phone;
    private String country;
    private String city;
    private String state;
    private String street;
    private String addressLine2;
    private String zipCode;
    private AddressType addressType;
    private Boolean isDefault;
    private String label;
}
