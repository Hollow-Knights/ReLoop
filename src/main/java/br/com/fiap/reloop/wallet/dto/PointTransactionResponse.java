package br.com.fiap.reloop.wallet.dto;

import java.time.Instant;

import br.com.fiap.reloop.wallet.model.PointTransaction;
import br.com.fiap.reloop.wallet.model.PointTransactionType;

public record PointTransactionResponse(
        long id, PointTransactionType type, int points, String description, Instant createdAt) {

    public static PointTransactionResponse from(PointTransaction transaction) {
        return new PointTransactionResponse(transaction.id(), transaction.type(), transaction.points(),
                transaction.description(), transaction.createdAt());
    }
}
