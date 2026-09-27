package com.agrolink.app.service;

import com.agrolink.app.dto.InvoiceDTO;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.model.Invoice;
import com.agrolink.app.model.Order;

import java.util.List;

public interface InvoiceService {

    List<InvoiceDTO> listForUser(String userId);

    PageResponse<InvoiceDTO> listAll(int page, int size);

    void deleteById(String invoiceId);

    InvoiceDTO getById(String invoiceId, String userId, boolean isAdmin);

    InvoiceDTO generateOrGetByOrder(String orderId, String userId, boolean isAdmin);

    Invoice syncForOrder(Order order);
}