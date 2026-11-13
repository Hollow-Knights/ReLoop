package br.com.fiap.reloop.reward.model;

import java.time.Instant;

public record Redemption(
        Long id, Long rewardId, String rewardName, int pointsSpent,
        int remainingPoints, String couponCode, Instant redeemedAt) {
}
