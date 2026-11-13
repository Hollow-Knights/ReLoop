package br.com.fiap.reloop.reward.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.fiap.reloop.reward.model.Redemption;
import br.com.fiap.reloop.reward.model.Reward;
import br.com.fiap.reloop.shared.persistence.InMemoryPointsLock;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("bootstrap")
public class InMemoryRewardRepository implements RewardRepository, RedemptionRepository {

    private final InMemoryPointsLock lock;
    private final WalletRepository wallets;
    private final Map<Long, Reward> rewards = new LinkedHashMap<>();
    private final Map<Attempt, Redemption> redemptions = new HashMap<>();
    private long nextRedemptionId = 1L;

    public InMemoryRewardRepository(InMemoryPointsLock lock, WalletRepository wallets) {
        this.lock = lock;
        this.wallets = wallets;
        rewards.put(10L, new Reward(10L, "Cupom de frete grátis", "Frete grátis em compra parceira",
                "Loja Exemplo", 20, 10, true));
        rewards.put(11L, new Reward(11L, "Desconto de 10%", "Desconto em produtos selecionados",
                "Loja Verde", 50, 20, true));
        rewards.put(12L, new Reward(12L, "Vale de demonstração", "Benefício com uma unidade disponível",
                "Loja Exemplo", 20, 1, true));
        rewards.put(13L, new Reward(13L, "Cupom de campanha encerrada", "Benefício de uma campanha anterior",
                "Loja Verde", 20, 3, false));
    }

    @Override
    public List<Reward> findAll() {
        synchronized (lock) {
            return List.copyOf(rewards.values());
        }
    }

    @Override
    public Redemption redeem(Long userId, Long rewardId, String idempotencyKey) {
        synchronized (lock) {
            var attempt = new Attempt(userId, idempotencyKey);
            Redemption previous = redemptions.get(attempt);
            if (previous != null) {
                if (!previous.rewardId().equals(rewardId)) {
                    throw new RedemptionRejected(Rejection.IDEMPOTENCY_KEY_CONFLICT);
                }
                return previous;
            }

            Reward reward = rewards.get(rewardId);
            if (reward == null) {
                throw new RedemptionRejected(Rejection.REWARD_NOT_FOUND);
            }
            if (!reward.active() || reward.availableQuantity() == 0) {
                throw new RedemptionRejected(Rejection.REWARD_UNAVAILABLE);
            }
            var wallet = wallets.findByUserId(userId)
                    .orElseThrow(() -> new RedemptionRejected(Rejection.WALLET_NOT_FOUND));
            if (wallet.availablePoints() < reward.costPoints()) {
                throw new RedemptionRejected(Rejection.INSUFFICIENT_POINTS);
            }

            long id = nextRedemptionId;
            Instant now = Instant.now();
            String coupon = "RELOOP-" + id + "-" + UUID.randomUUID();
            var redemption = new Redemption(id, rewardId, reward.name(), reward.costPoints(),
                    wallet.availablePoints() - reward.costPoints(), coupon, now);
            Reward updated = reward.takeOne();
            wallets.debitRedemption(userId, id, reward.costPoints(), reward.name(), now);
            rewards.put(rewardId, updated);
            redemptions.put(attempt, redemption);
            nextRedemptionId++;
            return redemption;
        }
    }

    @Override
    public List<Redemption> findByUserId(Long userId) {
        synchronized (lock) {
            return redemptions.entrySet().stream()
                    .filter(entry -> entry.getKey().userId().equals(userId))
                    .map(Map.Entry::getValue)
                    .toList();
        }
    }

    private record Attempt(Long userId, String key) {
    }
}
