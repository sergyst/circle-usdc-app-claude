package com.circle.usdcapp.repository;

import com.circle.usdcapp.model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, String> {
    List<LedgerTransaction> findAllByOrderByCreatedAtDesc();

    List<LedgerTransaction> findByWalletIdOrderByCreatedAtDesc(String walletId);

    Optional<LedgerTransaction> findByCircleTransactionId(String circleTransactionId);
}
