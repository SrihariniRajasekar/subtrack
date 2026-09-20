package com.subtrack.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {

    public enum Status {
        PAID,
        PENDING,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "due_at", nullable = false)
    private LocalDate dueAt;

    // The breakdown of how this invoice's total was calculated.
    // One item for a plain renewal, two items (old plan + new plan) when prorated.
    // EAGER + cascade ALL: line items are always loaded with their invoice and
    // fully owned by it (deleting an invoice deletes its line items too).
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = Status.PENDING;
        }
        if (this.issuedAt == null) {
            this.issuedAt = LocalDate.now();
        }
        if (this.dueAt == null) {
            this.dueAt = this.issuedAt.plusDays(7);
        }
    }

    // --- Constructors ---

    public Invoice() {
    }

    public Invoice(Subscription subscription, BigDecimal amount) {
        this.subscription = subscription;
        this.amount = amount;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDate getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDate issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDate getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDate dueAt) {
        this.dueAt = dueAt;
    }

    public List<InvoiceLineItem> getLineItems() {
        return lineItems;
    }

    // Adds a line item and keeps both sides of the relationship in sync,
    // which JPA requires for the foreign key to be set correctly.
    public void addLineItem(String description, BigDecimal amount) {
        InvoiceLineItem item = new InvoiceLineItem(this, description, amount);
        this.lineItems.add(item);
    }
}
