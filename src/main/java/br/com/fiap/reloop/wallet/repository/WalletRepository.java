package br.com.fiap.reloop.wallet.repository;

import java.time.Instant;
import java.util.Optional;

import br.com.fiap.reloop.wallet.model.Wallet;

public interface WalletRepository {

    void open(Long userId);

    Optional<Wallet> findByUserId(Long userId);

    /** Registra o crédito na mesma unidade de trabalho da conclusão do descarte. */
    void creditDisposal(Long userId, Long disposalId, int points, String itemName, Instant createdAt);

    /** Registra o débito na mesma unidade de trabalho do resgate. */
    void debitRedemption(Long userId, Long redemptionId, int points, String rewardName, Instant createdAt);
}
