package com.circle.usdcapp.repository;

import com.circle.usdcapp.model.MintOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MintOrderRepository extends JpaRepository<MintOrder, String> {
    List<MintOrder> findAllByOrderByCreatedAtDesc();

    Optional<MintOrder> findByCircleReferenceId(String circleReferenceId);

    Optional<MintOrder> findByReferenceId(String referenceId);

    /**
     * Oldest still-open BUY order for a given amount - used as a best-effort match
     * for incoming wire deposits that don't carry back our reference code.
     */
    Optional<MintOrder> findFirstByTypeAndStatusAndAmountUsdOrderByCreatedAtAsc(
            MintOrder.OrderType type, MintOrder.OrderStatus status, BigDecimal amountUsd);
}
