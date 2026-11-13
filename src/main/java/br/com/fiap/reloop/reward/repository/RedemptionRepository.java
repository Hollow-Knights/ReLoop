package br.com.fiap.reloop.reward.repository;

import java.util.List;

import br.com.fiap.reloop.reward.model.Redemption;

public interface RedemptionRepository {

    /**
     * Resolve a idempotência antes de verificar saldo e estoque. Grava o débito,
     * a baixa de estoque e o resgate com seu cupom em uma única operação.
     */
    Redemption redeem(Long userId, Long rewardId, String idempotencyKey);

    List<Redemption> findByUserId(Long userId);

    enum Rejection {
        REWARD_NOT_FOUND,
        REWARD_UNAVAILABLE,
        INSUFFICIENT_POINTS,
        IDEMPOTENCY_KEY_CONFLICT,
        WALLET_NOT_FOUND
    }

    class RedemptionRejected extends RuntimeException {

        private final Rejection reason;

        public RedemptionRejected(Rejection reason) {
            this.reason = reason;
        }

        public Rejection reason() {
            return reason;
        }
    }
}
