package com.subtrack.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: boots the full Spring context (real controller, real
 * service, real JPA layer) against the H2 in-memory database, and hits
 * actual HTTP endpoints through MockMvc. This is what proves the layers
 * are wired together correctly, as opposed to the unit tests which check
 * SubscriptionService's logic in isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createCustomer_thenFetchById_returnsSameCustomer() throws Exception {
        String requestBody = """
                {"name":"Priya Iyer","email":"priya.integration@example.com"}
                """;

        String response = mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Priya Iyer"))
                .andExpect(jsonPath("$.email").value("priya.integration@example.com"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        // Extract the generated id and confirm GET returns the same customer.
        // Cast through Number first - JsonPath returns Integer for small JSON
        // numbers by default, which can't be auto-unboxed directly to long.
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya Iyer"));
    }

    @Test
    void createCustomer_withInvalidEmail_returns400() throws Exception {
        String requestBody = """
                {"name":"Bad Email Test","email":"not-an-email"}
                """;

        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCustomer_thatDoesNotExist_returns404() throws Exception {
        mockMvc.perform(get("/customers/999999"))
                .andExpect(status().isNotFound());
    }
}
