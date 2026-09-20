package com.subtrack.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    public enum Status {
        ACTIVE,
        CANCELLED,
        PAST_DUE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // Tracks a mid-cycle plan change, so renew() knows whether to prorate.
    // Null means no plan change happened during the current billing period.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "previous_plan_id", nullable = true)
    private Plan previousPlan;

    @Column(name = "plan_changed_at", nullable = true)
    private LocalDate planChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "current_period_end", nullable = false)
    private LocalDate currentPeriodEnd;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = Status.ACTIVE;
        }
        if (this.startDate == null) {
            this.startDate = LocalDate.now();
        }
        if (this.currentPeriodEnd == null) {
            this.currentPeriodEnd = computePeriodEnd(this.startDate);
        }
    }

    // Computes the period end based on plan's billing interval.
    // Called here too so it's available before persistence if plan is already set.
    public LocalDate computePeriodEnd(LocalDate from) {
        if (plan == null) {
            return from.plusMonths(1);
        }
        return plan.getBillingInterval() == Plan.BillingInterval.YEARLY
                ? from.plusYears(1)
                : from.plusMonths(1);
    }

    // --- Constructors ---

    public Subscription() {
    }

    public Subscription(Customer customer, Plan plan) {
        this.customer = customer;
        this.plan = plan;
        // Set defaults here directly, rather than relying only on @PrePersist -
        // that hook only fires when Hibernate actually persists the entity,
        // so a plain "new Subscription(...)" (e.g. in a unit test with a
        // mocked repository) would otherwise leave these fields null.
        this.status = Status.ACTIVE;
        this.startDate = LocalDate.now();
        this.currentPeriodEnd = computePeriodEnd(this.startDate);
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public Plan getPreviousPlan() {
        return previousPlan;
    }

    public void setPreviousPlan(Plan previousPlan) {
        this.previousPlan = previousPlan;
    }

    public LocalDate getPlanChangedAt() {
        return planChangedAt;
    }

    public void setPlanChangedAt(LocalDate planChangedAt) {
        this.planChangedAt = planChangedAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(LocalDate currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }
}
