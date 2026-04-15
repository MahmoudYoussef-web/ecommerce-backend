package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.tenant.TenantContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = Long.class)
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class BaseEntity  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;


    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;


    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    public void assignTenant() {


        if (this.tenantId != null && this.tenantId > 0) return;

        Long tenant = TenantContext.getOrNull();


        if (tenant == null) {
            this.tenantId = 1L;
            return;
        }

        this.tenantId = tenant;
    }
}