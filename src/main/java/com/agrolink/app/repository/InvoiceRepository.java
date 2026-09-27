package com.agrolink.app.repository;

import com.agrolink.app.model.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends MongoRepository<Invoice, String> {

    Optional<Invoice> findByOrderId(String orderId);

    List<Invoice> findByBuyerIdOrFarmerIdOrderByCreatedAtDesc(String buyerId, String farmerId);

    Page<Invoice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<Invoice> findFirstByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(String prefix);
}