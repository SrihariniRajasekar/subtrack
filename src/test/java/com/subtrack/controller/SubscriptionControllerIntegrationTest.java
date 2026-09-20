package com.subtrack.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test covering the full billing lifecycle end to end:
 * customer -> plan -> subscription -> renewal -> invoice.
 * Runs against the real H2 database via the real Spring context, so it
 * proves the whole stack (controller, service, JPA, entity relationships)
 * works together correctly, not just the isolated proration math.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // JsonPath returns Integer for small JSON numbers by default, which can't
    // be auto-unboxed directly to long - casting through Number fixes it.
    private long readId(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void fullBillingFlow_createSubscriptionAndRenew_generatesCorrectInvoice() throws Exception {
        // 1. Create a customer
        String customerResponse = mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Integration Test Customer","email":"flow.test@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long customerId = readId(customerResponse);

        // 2. Create a plan
        String planResponse = mockMvc.perform(post("/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flow Test Plan","price":45.00,"billingInterval":"MONTHLY"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long planId = readId(planResponse);

        // 3. Create a subscription linking them
        String subscriptionResponse = mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"customerId\":%d,\"planId\":%d}", customerId, planId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long subscriptionId = readId(subscriptionResponse);

        // 4. Renew it - no plan change occurred, so expect one line item at full price
        mockMvc.perform(post("/subscriptions/" + subscriptionId + "/renew"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(45.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.lineItems.length()").value(1));

        // 5. Confirm the invoice now shows up under the subscription's invoice list
        mockMvc.perform(get("/subscriptions/" + subscriptionId + "/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(45.00));
    }

    @Test
    void cancelSubscription_thenRenew_returns409() throws Exception {
        String customerResponse = mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cancel Test Customer","email":"cancel.test@example.com"}
                                """))
                .andReturn().getResponse().getContentAsString();
        long customerId = readId(customerResponse);

        String planResponse = mockMvc.perform(post("/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cancel Test Plan","price":20.00,"billingInterval":"MONTHLY"}
                                """))
                .andReturn().getResponse().getContentAsString();
        long planId = readId(planResponse);

        String subscriptionResponse = mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"customerId\":%d,\"planId\":%d}", customerId, planId)))
                .andReturn().getResponse().getContentAsString();
        long subscriptionId = readId(subscriptionResponse);

        mockMvc.perform(patch("/subscriptions/" + subscriptionId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/subscriptions/" + subscriptionId + "/renew"))
                .andExpect(status().isConflict());
    }
}
