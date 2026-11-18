package br.com.fiap.reloop.persistence.postgres;

import java.util.List;
import java.util.UUID;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.RedemptionRow;
import br.com.fiap.reloop.reward.model.Redemption;
import br.com.fiap.reloop.reward.model.Reward;
import br.com.fiap.reloop.reward.repository.RedemptionRepository;
import br.com.fiap.reloop.reward.repository.RewardRepository;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional
public class JpaRewardAdapter implements RewardRepository, RedemptionRepository {
    private final JpaStores.Rewards rewards;
    private final JpaStores.Redemptions redemptions;
    private final JpaStores.Wallets walletRows;
    private final WalletRepository wallets;
    public JpaRewardAdapter(JpaStores.Rewards rewards, JpaStores.Redemptions redemptions,
            JpaStores.Wallets walletRows, WalletRepository wallets) {
        this.rewards = rewards; this.redemptions = redemptions; this.walletRows = walletRows; this.wallets = wallets;
    }
    @Override @Transactional(readOnly = true)
    public List<Reward> findAll() {
        return rewards.findAll(Sort.by("id")).stream().map(r -> new Reward(r.id, r.name, r.description,
                r.partnerName, r.costPoints, r.availableQuantity, r.active)).toList();
    }
    @Override public Redemption redeem(Long userId, Long rewardId, String key) {
        // All redemptions acquire wallet BEFORE reward; IoT never acquires a reward lock.
        var wallet = walletRows.lock(userId).orElseThrow(() -> rejected(Rejection.WALLET_NOT_FOUND));
        var previous = redemptions.findByUserIdAndIdempotencyKey(userId, key);
        if (previous.isPresent()) {
            if (!previous.get().rewardId.equals(rewardId)) throw rejected(Rejection.IDEMPOTENCY_KEY_CONFLICT);
            return domain(previous.get());
        }
        var reward = rewards.lock(rewardId).orElseThrow(() -> rejected(Rejection.REWARD_NOT_FOUND));
        if (!reward.active || reward.availableQuantity == 0) throw rejected(Rejection.REWARD_UNAVAILABLE);
        if (wallet.availablePoints < reward.costPoints) throw rejected(Rejection.INSUFFICIENT_POINTS);
        var row = new RedemptionRow();
        row.userId = userId; row.rewardId = rewardId; row.rewardName = reward.name;
        row.pointsSpent = reward.costPoints; row.remainingPoints = wallet.availablePoints - reward.costPoints;
        row.couponCode = "RELOOP-" + UUID.randomUUID(); row.idempotencyKey = key; row.redeemedAt = DatabaseTime.now();
        redemptions.saveAndFlush(row);
        wallets.debitRedemption(userId, row.id, row.pointsSpent, reward.name, row.redeemedAt);
        reward.availableQuantity--;
        rewards.flush();
        return domain(row);
    }
    @Override @Transactional(readOnly = true)
    public List<Redemption> findByUserId(Long userId) {
        return redemptions.findByUserId(userId).stream().map(JpaRewardAdapter::domain).toList();
    }
    private static Redemption domain(RedemptionRow r) {
        return new Redemption(r.id, r.rewardId, r.rewardName, r.pointsSpent, r.remainingPoints, r.couponCode, r.redeemedAt);
    }
    private static RedemptionRejected rejected(Rejection reason) { return new RedemptionRejected(reason); }
}
