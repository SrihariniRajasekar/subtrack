package com.subtrack.dto;

import jakarta.validation.constraints.NotNull;

// What the client sends when creating a subscription.
// Keeps the API simple: just reference existing customer/plan by id,
// rather than requiring the full nested Customer/Plan JSON.
public class SubscriptionRequest {

    @NotNull(message = "customerId is required")
    private Long customerId;

    @NotNull(message = "planId is required")
    private Long planId;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }
}
