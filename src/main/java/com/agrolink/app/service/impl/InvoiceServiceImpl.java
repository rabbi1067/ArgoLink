package com.agrolink.app.service.impl;

import com.agrolink.app.dto.InvoiceDTO;
import com.agrolink.app.dto.OrderResponseRecord;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.model.Invoice;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.InvoiceRepository;
import com.agrolink.app.repository.OrderRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final int MONEY_SCALE = 2;
    private static final int TAX_SCALE = 2;

    @Value("${agrolink.invoice.delivery-charge:0}")
    private int deliveryCharge;

    @Value("${agrolink.invoice.tax-rate-percent:0}")
    private int taxRatePercent;

    @Value("${agrolink.invoice.discount:0}")
    private int fixedDiscount;

    @Value("${agrolink.invoice.payment-due-days:7}")
    private int paymentDueDays;

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Override
    public List<InvoiceDTO> listForUser(String userId) {
        return invoiceRepository.findByBuyerIdOrFarmerIdOrderByCreatedAtDesc(userId, userId)
                .stream()
                .map(InvoiceDTO::from)
                .toList();
    }

    @Override
    public PageResponse<InvoiceDTO> listAll(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponse.from(invoiceRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(InvoiceDTO::from));
    }

    @Override
    public void deleteById(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        invoiceRepository.delete(invoice);
    }

    @Override
    public InvoiceDTO getById(String invoiceId, String userId, boolean isAdmin) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        if (!isAdmin && !invoice.getBuyerId().equals(userId) && !invoice.getFarmerId().equals(userId)) {
            throw new BusinessRuleException("You are not allowed to view this invoice", 403);
        }
        return InvoiceDTO.from(invoice);
    }

    @Override
    public InvoiceDTO generateOrGetByOrder(String orderId, String userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        if (!isAdmin && !order.getBuyerId().equals(userId) && !order.getFarmerId().equals(userId)) {
            throw new BusinessRuleException("You are not allowed to view this order", 403);
        }
        return InvoiceDTO.from(syncForOrder(order));
    }

    @Override
    public Invoice syncForOrder(Order order) {
        Invoice invoice = invoiceRepository.findByOrderId(order.getId())
                .orElseGet(() -> Invoice.builder()
                        .orderId(order.getId())
                        .invoiceNumber(nextInvoiceNumber())
                        .verificationToken(UUID.randomUUID().toString())
                        .build());

        BigDecimal quantity = order.getAgreedQuantity();
        BigDecimal unitPrice = order.getAgreedPricePerUnit();
        BigDecimal subtotal = quantity.multiply(unitPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal delivery = BigDecimal.valueOf(deliveryCharge).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal
                .multiply(BigDecimal.valueOf(taxRatePercent))
                .divide(BigDecimal.valueOf(100), TAX_SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = BigDecimal.valueOf(fixedDiscount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(delivery).add(tax).subtract(discount)
                .max(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        invoice.setBuyerId(order.getBuyerId());
        invoice.setFarmerId(order.getFarmerId());
        invoice.setBuyerName(nameOf(order.getBuyerId()));
        invoice.setFarmerName(nameOf(order.getFarmerId()));
        invoice.setBuyerAddress(order.getDeliveryAddress());
        invoice.setCropName(order.getCropName());
        invoice.setQuantity(quantity);
        invoice.setUnitPriceBt(unitPrice);
        invoice.setSubtotalBt(subtotal);
        invoice.setDeliveryChargeBt(delivery);
        invoice.setTaxBt(tax);
        invoice.setDiscountBt(discount);
        invoice.setTotalBt(total);
        invoice.setDeliveryAddress(order.getDeliveryAddress());
        invoice.setPaymentStatus(OrderResponseRecord.paymentStatusFor(order));
        invoice.setDueDate(Instant.now().plusSeconds(paymentDueDays * 86400L));

        return saveWithUniqueNumber(invoice);
    }


    private Invoice saveWithUniqueNumber(Invoice invoice) {
        DuplicateKeyException lastFailure = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return invoiceRepository.save(invoice);
            } catch (DuplicateKeyException duplicate) {
                lastFailure = duplicate;
                invoice.setInvoiceNumber(nextInvoiceNumber());
            }
        }
        throw lastFailure;
    }

    private String nameOf(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        return userRepository.findById(userId)
                .map(User::getName)
                .orElse(userId);
    }


    private String nextInvoiceNumber() {
        String year = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneId.systemDefault())
                .format(Instant.now());
        String prefix = "AGR-INV-" + year + "-";
        long sequence = invoiceRepository
                .findFirstByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(prefix)
                .map(Invoice::getInvoiceNumber)
                .map(last -> last.substring(prefix.length()))
                .map(last -> {
                    try {
                        return Long.parseLong(last);
                    } catch (NumberFormatException notANumber) {
                        return 0L;
                    }
                })
                .orElse(0L);
        return String.format("%s%06d", prefix, sequence + 1);
    }
}