package br.com.fiap.reloop.disposal.dto;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.fiap.reloop.disposal.model.DisposalStatus;

public record DisposalHistoryResponse(
        Long id,
        String itemName,
        Long categoryId,
        DisposalStatus status,
        BigDecimal weightKg,
        int awardedPoints,
        Instant createdAt) {
}
