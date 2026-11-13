package br.com.fiap.reloop.reward.dto;

import br.com.fiap.reloop.reward.model.Reward;

public record RewardResponse(
        Long id, String name, String description, String partnerName,
        int costPoints, int availableQuantity, boolean active) {

    public static RewardResponse from(Reward reward) {
        return new RewardResponse(reward.id(), reward.name(), reward.description(), reward.partnerName(),
                reward.costPoints(), reward.availableQuantity(), reward.active());
    }
}
