package com.mahmoud.ecommerce_backend.dto.address;

import com.mahmoud.ecommerce_backend.enums.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressRequest {

    private String fullName;

    @Size(max = 20)
    private String phone;

    private AddressType addressType;

    @NotBlank
    private String country;

    @NotBlank
    private String city;

    @NotBlank
    private String street;

    @NotBlank
    private String zipCode;

    private String addressLine2;

    private String state;

    private Boolean isDefault;

    private String label;
}
