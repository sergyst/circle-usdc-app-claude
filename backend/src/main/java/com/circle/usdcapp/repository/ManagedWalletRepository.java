package com.circle.usdcapp.repository;

import com.circle.usdcapp.model.ManagedWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagedWalletRepository extends JpaRepository<ManagedWallet, String> {
    Optional<ManagedWallet> findByCircleWalletId(String circleWalletId);
}
