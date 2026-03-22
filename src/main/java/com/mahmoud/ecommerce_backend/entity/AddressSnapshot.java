package com.mahmoud.ecommerce_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class AddressSnapshot {

    @NotBlank
    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String addressLine1;

    @Column(length = 255)
    private String addressLine2;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String postalCode;

    @NotBlank
    @Column(nullable = false, length = 60)
    private String country;

    public static AddressSnapshot from(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }

        return AddressSnapshot.builder()
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }
}