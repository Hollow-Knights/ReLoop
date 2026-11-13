package br.com.fiap.reloop.wallet.model;

import java.time.Instant;

public record PointTransaction(
        long id, PointTransactionType type, int points, String description, Instant createdAt,
        Long disposalId, Long redemptionId) {

    public PointTransaction {
        if (points <= 0) {
            throw new IllegalArgumentException("Uma movimentação deve ter pontos positivos.");
        }
    }
}
