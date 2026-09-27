package com.agrolink.app.repository;

import com.agrolink.app.model.Order;
import com.agrolink.app.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByBuyerId(String buyerId);

    List<Order> findByFarmerId(String farmerId);

    List<Order> findByOrderStatus(OrderStatus orderStatus);

    List<Order> findByBuyerIdAndOrderStatus(String buyerId, OrderStatus orderStatus);

    List<Order> findByFarmerIdAndOrderStatus(String farmerId, OrderStatus orderStatus);

    Optional<Order> findByOfferId(String offerId);

    Optional<Order> findByTransactionId(String transactionId);

    List<Order> findByCreatedAtAfterOrderByCreatedAtDesc(java.time.Instant since);

    long countByOrderStatus(OrderStatus orderStatus);

    List<Order> findTop10ByOrderByCreatedAtDesc();
    List<Order> findTop5ByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<Order> findTop5ByFarmerIdOrderByCreatedAtDesc(String farmerId);


    Optional<Order> findByIdAndDeletedFalse(String id);

    List<Order> findByDeletedFalseAndOrderStatus(OrderStatus orderStatus);


    Page<Order> findByBuyerIdOrderByCreatedAtDesc(String buyerId, Pageable pageable);

    Page<Order> findByFarmerIdOrderByCreatedAtDesc(String farmerId, Pageable pageable);

    Page<Order> findByBuyerIdAndOrderStatusOrderByCreatedAtDesc(String buyerId, OrderStatus orderStatus, Pageable pageable);

    Page<Order> findByFarmerIdAndOrderStatusOrderByCreatedAtDesc(String farmerId, OrderStatus orderStatus, Pageable pageable);

    Page<Order> findByBuyerIdAndDeletedFalseOrderByCreatedAtDesc(String buyerId, Pageable pageable);

    Page<Order> findByFarmerIdAndDeletedFalseOrderByCreatedAtDesc(String farmerId, Pageable pageable);

    Page<Order> findByBuyerIdAndDeletedFalseAndOrderStatusOrderByCreatedAtDesc(String buyerId, OrderStatus orderStatus, Pageable pageable);

    Page<Order> findByFarmerIdAndDeletedFalseAndOrderStatusOrderByCreatedAtDesc(String farmerId, OrderStatus orderStatus, Pageable pageable);

    Page<Order> findByBuyerIdAndDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
            String buyerId, OrderStatus excludedStatus, Pageable pageable);

    Page<Order> findByFarmerIdAndDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
            String farmerId, OrderStatus excludedStatus, Pageable pageable);

    Page<Order> findByBuyerIdAndDeletedFalseAndOrderStatusNotAndOrderStatusOrderByCreatedAtDesc(
            String buyerId, OrderStatus excludedStatus, OrderStatus orderStatus, Pageable pageable);

    Page<Order> findByFarmerIdAndDeletedFalseAndOrderStatusNotAndOrderStatusOrderByCreatedAtDesc(
            String farmerId, OrderStatus excludedStatus, OrderStatus orderStatus, Pageable pageable);


    Page<Order> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByDeletedFalseAndOrderStatusOrderByCreatedAtDesc(OrderStatus orderStatus, Pageable pageable);

    Page<Order> findByDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
            OrderStatus excludedStatus, Pageable pageable);

    Page<Order> findByDeletedFalseAndOrderStatusNotAndOrderStatusOrderByCreatedAtDesc(
            OrderStatus excludedStatus, OrderStatus orderStatus, Pageable pageable);


    long countByBuyerIdAndDeletedFalseAndOrderStatus(String buyerId, OrderStatus orderStatus);

    long countByFarmerIdAndDeletedFalseAndOrderStatus(String farmerId, OrderStatus orderStatus);

    long countByBuyerIdAndDeletedFalse(String buyerId);

    long countByFarmerIdAndDeletedFalse(String farmerId);
}
