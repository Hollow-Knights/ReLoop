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
        return withState(DisposalStatus.CANCELLED, weightKg, estimatedPoints, awardedPoints);
    }

    public Disposal recordWeight(BigDecimal weight, int points) {
        return withState(DisposalStatus.WAITING_DEPOSIT, weight, points, 0);
    }

    public Disposal openDoor() {
        return withState(DisposalStatus.DEPOSIT_IN_PROGRESS, weightKg, estimatedPoints, 0);
    }

    public Disposal complete() {
        return withState(DisposalStatus.COMPLETED, weightKg, estimatedPoints, estimatedPoints);
    }

    private Disposal withState(DisposalStatus newStatus, BigDecimal weight, Integer estimate, int award) {
        return new Disposal(id, userId, deviceId, categoryId, itemName, photoId,
                newStatus, weight, estimate, award, createdAt);
    }
}
