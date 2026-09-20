package com.subtrack.controller;

import com.subtrack.dto.ChangePlanRequest;
import com.subtrack.dto.SubscriptionRequest;
import com.subtrack.entity.Invoice;
import com.subtrack.entity.Subscription;
import com.subtrack.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<Subscription> createSubscription(@Valid @RequestBody SubscriptionRequest request) {
        Subscription saved = subscriptionService.createSubscription(request.getCustomerId(), request.getPlanId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Subscription> getSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getSubscription(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Subscription> cancelSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.cancelSubscription(id));
    }

    @PatchMapping("/{id}/plan")
    public ResponseEntity<Subscription> changePlan(@PathVariable Long id, @Valid @RequestBody ChangePlanRequest request) {
        return ResponseEntity.ok(subscriptionService.changePlan(id, request.getPlanId()));
    }

    @PostMapping("/{id}/renew")
    public ResponseEntity<Invoice> renewSubscription(@PathVariable Long id) {
        Invoice invoice = subscriptionService.renewSubscription(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    @GetMapping("/{id}/invoices")
    public ResponseEntity<List<Invoice>> getInvoices(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getInvoices(id));
    }
}
