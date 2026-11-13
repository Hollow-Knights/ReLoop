package br.com.fiap.reloop.disposal.dto;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import br.com.fiap.reloop.disposal.model.DisposalStatus;

public record DisposalResponse(
        Long id,
        String deviceId,
        Long categoryId,
        String itemName,
        UUID photoId,
        URI photoUrl,
        DisposalStatus status,
        BigDecimal weightKg,
        Integer estimatedPoints,
        int awardedPoints,
        Instant createdAt) {
}
