package com.subtrack.repository;

import com.subtrack.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Spring Data JPA auto-generates the query from this method name -
    // no SQL needed. Finds all invoices belonging to a given subscription.
    List<Invoice> findBySubscriptionId(Long subscriptionId);
}
