package com.subtrack.service;

import com.subtrack.entity.Customer;
import com.subtrack.entity.Invoice;
import com.subtrack.entity.Plan;
import com.subtrack.entity.Subscription;
import com.subtrack.repository.CustomerRepository;
import com.subtrack.repository.InvoiceRepository;
import com.subtrack.repository.PlanRepository;
import com.subtrack.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionService, the class that holds all of SubTrack's
 * real business logic - subscription creation, plan changes, and renewal
 * with day-based proration.
 *
 * Repositories are mocked (Mockito) so these tests run purely in memory,
 * with no real database involved - that's what makes them "unit" tests
 * rather than "integration" tests.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Customer customer;
    private Plan basicPlan;
    private Plan proPlan;

    @BeforeEach
    void setUp() {
        customer = new Customer("Rahul Sharma", "rahul@example.com");
        customer.setId(1L);

        basicPlan = new Plan("Basic", new BigDecimal("30.00"), Plan.BillingInterval.MONTHLY);
        basicPlan.setId(1L);

        proPlan = new Plan("Pro", new BigDecimal("60.00"), Plan.BillingInterval.MONTHLY);
        proPlan.setId(2L);
    }

    // --- createSubscription ---

    @Test
    void createSubscription_success_returnsActiveSubscription() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(planRepository.findById(1L)).thenReturn(Optional.of(basicPlan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = subscriptionService.createSubscription(1L, 1L);

        assertEquals(Subscription.Status.ACTIVE, result.getStatus());
        assertEquals(basicPlan, result.getPlan());
        assertEquals(customer, result.getCustomer());
    }

    @Test
    void createSubscription_customerNotFound_throws404() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.createSubscription(99L, 1L));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void createSubscription_planNotFound_throws404() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(planRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.createSubscription(1L, 99L));

        assertEquals(404, ex.getStatusCode().value());
    }

    // --- cancelSubscription ---

    @Test
    void cancelSubscription_setsStatusToCancelled() {
        Subscription subscription = activeSubscription(basicPlan, LocalDate.now(), LocalDate.now().plusMonths(1));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = subscriptionService.cancelSubscription(1L);

        assertEquals(Subscription.Status.CANCELLED, result.getStatus());
    }

    // --- changePlan ---

    @Test
    void changePlan_recordsPreviousPlanAndSwitchDate() {
        Subscription subscription = activeSubscription(basicPlan, LocalDate.now(), LocalDate.now().plusMonths(1));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(2L)).thenReturn(Optional.of(proPlan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = subscriptionService.changePlan(1L, 2L);

        assertEquals(proPlan, result.getPlan());
        assertEquals(basicPlan, result.getPreviousPlan());
        assertEquals(LocalDate.now(), result.getPlanChangedAt());
    }

    @Test
    void changePlan_sameplan_isNoOpAndDoesNotRecordChange() {
        Subscription subscription = activeSubscription(basicPlan, LocalDate.now(), LocalDate.now().plusMonths(1));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(1L)).thenReturn(Optional.of(basicPlan));

        Subscription result = subscriptionService.changePlan(1L, 1L);

        assertNull(result.getPreviousPlan());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void changePlan_onCancelledSubscription_throws409() {
        Subscription subscription = activeSubscription(basicPlan, LocalDate.now(), LocalDate.now().plusMonths(1));
        subscription.setStatus(Subscription.Status.CANCELLED);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.changePlan(1L, 2L));

        assertEquals(409, ex.getStatusCode().value());
    }

    // --- renewSubscription: the proration logic ---

    @Test
    void renew_noPlanChange_chargesFullPriceAndAdvancesPeriod() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 2, 1);
        Subscription subscription = activeSubscription(basicPlan, start, end);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice invoice = subscriptionService.renewSubscription(1L);

        assertEquals(0, new BigDecimal("30.00").compareTo(invoice.getAmount()));
        assertEquals(1, invoice.getLineItems().size());
        assertEquals(end, subscription.getStartDate());
        assertEquals(end.plusMonths(1), subscription.getCurrentPeriodEnd());
    }

    @Test
    void renew_withMidCyclePlanChange_proratesCorrectlyBetweenBothPlans() {
        // 30-day period, plan changed exactly 10 days in ->
        // expect 10 days at Basic's daily rate + 20 days at Pro's daily rate
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate changedAt = LocalDate.of(2026, 1, 11);
        LocalDate end = LocalDate.of(2026, 1, 31);

        Subscription subscription = activeSubscription(proPlan, start, end);
        subscription.setPreviousPlan(basicPlan);
        subscription.setPlanChangedAt(changedAt);

        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice invoice = subscriptionService.renewSubscription(1L);

        // 10 days * (30/30) + 20 days * (60/30) = 10.00 + 40.00 = 50.00
        assertEquals(0, new BigDecimal("50.00").compareTo(invoice.getAmount()));
        assertEquals(2, invoice.getLineItems().size());
        assertEquals(0, new BigDecimal("10.00").compareTo(invoice.getLineItems().get(0).getAmount()));
        assertEquals(0, new BigDecimal("40.00").compareTo(invoice.getLineItems().get(1).getAmount()));

        // Plan-change tracking should reset for the new cycle
        assertNull(subscription.getPreviousPlan());
        assertNull(subscription.getPlanChangedAt());
    }

    @Test
    void renew_onCancelledSubscription_throws409() {
        Subscription subscription = activeSubscription(basicPlan, LocalDate.now(), LocalDate.now().plusMonths(1));
        subscription.setStatus(Subscription.Status.CANCELLED);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.renewSubscription(1L));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void renew_subscriptionNotFound_throws404() {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.renewSubscription(99L));

        assertEquals(404, ex.getStatusCode().value());
    }

    // --- test helper ---

    private Subscription activeSubscription(Plan plan, LocalDate start, LocalDate end) {
        Subscription subscription = new Subscription(customer, plan);
        subscription.setId(1L);
        subscription.setStatus(Subscription.Status.ACTIVE);
        subscription.setStartDate(start);
        subscription.setCurrentPeriodEnd(end);
        return subscription;
    }
}
