package br.com.fiap.reloop.reward.model;

public record Reward(
        Long id, String name, String description, String partnerName,
        int costPoints, int availableQuantity, boolean active) {

    public Reward {
        if (costPoints <= 0 || availableQuantity < 0) {
            throw new IllegalArgumentException("O benefício exige custo positivo e estoque não negativo.");
        }
    }

    public Reward takeOne() {
        return new Reward(id, name, description, partnerName, costPoints, availableQuantity - 1, active);
    }
}
