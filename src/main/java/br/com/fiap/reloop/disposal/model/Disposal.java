package br.com.fiap.reloop.disposal.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Disposal(
        Long id,
        Long userId,
        String deviceId,
        Long categoryId,
        String itemName,
        UUID photoId,
        DisposalStatus status,
        BigDecimal weightKg,
        Integer estimatedPoints,
        int awardedPoints,
        Instant createdAt) {

    public static Disposal start(Long id, Long userId, String deviceId, Long categoryId, String itemName,
            UUID photoId, Instant createdAt) {
        return new Disposal(id, userId, deviceId, categoryId, itemName, photoId,
                DisposalStatus.WAITING_WEIGHT, null, null, 0, createdAt);
    }

    public Disposal cancel() {
        if (status != DisposalStatus.WAITING_WEIGHT) {
            throw new IllegalStateException("Somente um descarte que aguarda pesagem pode ser cancelado.");
        }
        return new Disposal(id, userId, deviceId, categoryId, itemName, photoId,
                DisposalStatus.CANCELLED, weightKg, estimatedPoints, awardedPoints, createdAt);
    }
}
