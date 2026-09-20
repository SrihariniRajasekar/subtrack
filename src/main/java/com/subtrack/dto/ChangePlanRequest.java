package com.subtrack.dto;

import jakarta.validation.constraints.NotNull;

public class ChangePlanRequest {

    @NotNull(message = "planId is required")
    private Long planId;

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }
}
