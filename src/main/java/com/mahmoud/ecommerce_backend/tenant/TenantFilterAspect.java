package com.mahmoud.ecommerce_backend.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.mahmoud.ecommerce_backend.repository..*(..))")
    public void applyTenantFilter() {

        if (!TenantContext.isSet()) return;

        Session session = entityManager.unwrap(Session.class);

        Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("tenantId", TenantContext.getRequired());
    }
}