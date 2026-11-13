package br.com.fiap.reloop.reward.dto;

import java.time.Instant;

import br.com.fiap.reloop.reward.model.Redemption;

public record RedemptionResponse(
        Long redemptionId, Long rewardId, String rewardName, int pointsSpent,
        int remainingPoints, String couponCode, Instant redeemedAt) {

    public static RedemptionResponse from(Redemption redemption) {
        return new RedemptionResponse(redemption.id(), redemption.rewardId(), redemption.rewardName(),
                redemption.pointsSpent(), redemption.remainingPoints(), redemption.couponCode(),
                redemption.redeemedAt());
    }
}
