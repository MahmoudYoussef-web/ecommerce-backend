package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.AccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "chart_of_accounts",
        indexes = {
                @Index(name = "idx_coa_code", columnList = "code", unique = true),
                @Index(name = "idx_coa_type", columnList = "type")
        }
)
public class ChartOfAccount extends BaseEntity {

    @NotBlank
    @Column(nullable = false, unique = true, length = 50)
    private String code; // e.g. 1100, 4000

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private boolean active = true;
}