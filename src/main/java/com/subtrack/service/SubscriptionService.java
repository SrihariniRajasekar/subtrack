package com.subtrack.service;

import com.subtrack.entity.Customer;
import com.subtrack.entity.Invoice;
import com.subtrack.entity.Plan;
import com.subtrack.entity.Subscription;
import com.subtrack.repository.CustomerRepository;
import com.subtrack.repository.InvoiceRepository;
import com.subtrack.repository.PlanRepository;
import com.subtrack.repository.SubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PlanRepository planRepository;
    private final InvoiceRepository invoiceRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                CustomerRepository customerRepository,
                                PlanRepository planRepository,
                                InvoiceRepository invoiceRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.customerRepository = customerRepository;
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public Subscription createSubscription(Long customerId, Long planId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found with id: " + customerId));

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plan not found with id: " + planId));

        return subscriptionRepository.save(new Subscription(customer, plan));
    }

    public Subscription getSubscription(Long id) {
        return findOrThrow(id);
    }

    public Subscription cancelSubscription(Long id) {
        Subscription subscription = findOrThrow(id);
        subscription.setStatus(Subscription.Status.CANCELLED);
        return subscriptionRepository.save(subscription);
    }

    public List<Invoice> getInvoices(Long id) {
        findOrThrow(id); // ensures subscription exists, 404s otherwise
        return invoiceRepository.findBySubscriptionId(id);
    }

    /**
     * Changes a subscription's plan mid-cycle. Records the previous plan and the
     * date of the switch, so that renewSubscription() knows to prorate the
     * upcoming invoice between the old and new plan.
     */
    public Subscription changePlan(Long id, Long newPlanId) {
        Subscription subscription = findOrThrow(id);

        if (subscription.getStatus() == Subscription.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot change the plan of a cancelled subscription.");
        }

        Plan newPlan = planRepository.findById(newPlanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plan not found with id: " + newPlanId));

        if (newPlan.getId().equals(subscription.getPlan().getId())) {
            // No-op: switching to the same plan they're already on
            return subscription;
        }

        // Only record the FIRST plan change of this billing cycle as the
        // "previous" plan - if they change plans twice in one cycle, we still
        // want proration to be measured from the plan they started the cycle on.
        if (subscription.getPreviousPlan() == null) {
            subscription.setPreviousPlan(subscription.getPlan());
            subscription.setPlanChangedAt(LocalDate.now());
        }

        subscription.setPlan(newPlan);
        return subscriptionRepository.save(subscription);
    }

    /**
     * Renews a subscription: generates the invoice for the period just ending,
     * then advances the billing period forward.
     *
     * Proration: if the plan was changed mid-cycle (previousPlan/planChangedAt
     * are set), the invoice amount is split day-by-day between the old and new
     * plan's daily rate. Otherwise the full current plan price is charged.
     */
    @Transactional
    public Invoice renewSubscription(Long id) {
        Subscription subscription = findOrThrow(id);

        if (subscription.getStatus() == Subscription.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot renew a cancelled subscription.");
        }

        LocalDate periodStart = subscription.getStartDate();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd();

        Invoice invoice = new Invoice(subscription, BigDecimal.ZERO);
        BigDecimal total = buildLineItemsAndCalculateTotal(invoice, subscription, periodStart, periodEnd);
        invoice.setAmount(total);
        invoiceRepository.save(invoice);

        // Advance the subscription into its next billing period
        LocalDate newStart = periodEnd;
        LocalDate newEnd = subscription.computePeriodEnd(newStart);

        subscription.setStartDate(newStart);
        subscription.setCurrentPeriodEnd(newEnd);
        subscription.setStatus(Subscription.Status.ACTIVE);

        // Reset plan-change tracking - the new cycle starts clean
        subscription.setPreviousPlan(null);
        subscription.setPlanChangedAt(null);

        subscriptionRepository.save(subscription);

        return invoice;
    }

    /**
     * Core proration math. If no plan change occurred this cycle, adds a single
     * line item for the full current plan price. Otherwise adds two line items -
     * one for the old plan's partial usage, one for the new plan's - splitting
     * the charge by days using:
     *
     *   daily_rate = plan_price / total_days_in_period
     *   prorated_amount = daily_rate * days_on_that_plan
     *
     * Returns the invoice total (the sum of whichever line items were added).
     */
    private BigDecimal buildLineItemsAndCalculateTotal(Invoice invoice, Subscription subscription,
                                                         LocalDate periodStart, LocalDate periodEnd) {
        boolean planChangedThisCycle = subscription.getPreviousPlan() != null
                && subscription.getPlanChangedAt() != null
                && !subscription.getPlanChangedAt().isBefore(periodStart)
                && !subscription.getPlanChangedAt().isAfter(periodEnd);

        if (!planChangedThisCycle) {
            BigDecimal amount = subscription.getPlan().getPrice().setScale(2, RoundingMode.HALF_UP);
            invoice.addLineItem(
                    String.format("%s plan (%s - %s)", subscription.getPlan().getName(), periodStart, periodEnd),
                    amount);
            return amount;
        }

        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long daysOnOldPlan = ChronoUnit.DAYS.between(periodStart, subscription.getPlanChangedAt());
        long daysOnNewPlan = ChronoUnit.DAYS.between(subscription.getPlanChangedAt(), periodEnd);

        if (totalDays <= 0) {
            // Degenerate period (shouldn't normally happen) - fall back to full price
            BigDecimal amount = subscription.getPlan().getPrice().setScale(2, RoundingMode.HALF_UP);
            invoice.addLineItem(
                    String.format("%s plan (%s - %s)", subscription.getPlan().getName(), periodStart, periodEnd),
                    amount);
            return amount;
        }

        BigDecimal totalDaysBD = BigDecimal.valueOf(totalDays);

        BigDecimal oldPlanAmount = subscription.getPreviousPlan().getPrice()
                .divide(totalDaysBD, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(daysOnOldPlan))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal newPlanAmount = subscription.getPlan().getPrice()
                .divide(totalDaysBD, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(daysOnNewPlan))
                .setScale(2, RoundingMode.HALF_UP);

        invoice.addLineItem(
                String.format("%s plan (%d of %d days, %s - %s)",
                        subscription.getPreviousPlan().getName(), daysOnOldPlan, totalDays,
                        periodStart, subscription.getPlanChangedAt()),
                oldPlanAmount);

        invoice.addLineItem(
                String.format("%s plan (%d of %d days, %s - %s)",
                        subscription.getPlan().getName(), daysOnNewPlan, totalDays,
                        subscription.getPlanChangedAt(), periodEnd),
                newPlanAmount);

        return oldPlanAmount.add(newPlanAmount).setScale(2, RoundingMode.HALF_UP);
    }

    private Subscription findOrThrow(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Subscription not found with id: " + id));
    }
}
