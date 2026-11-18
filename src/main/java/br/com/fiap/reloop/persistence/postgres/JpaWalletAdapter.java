package br.com.fiap.reloop.persistence.postgres;

import java.time.Instant;
import java.util.Optional;
import br.com.fiap.reloop.disposal.model.DisposalStatus;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.TransactionRow;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.WalletRow;
import br.com.fiap.reloop.wallet.model.PointTransaction;
import br.com.fiap.reloop.wallet.model.PointTransactionType;
import br.com.fiap.reloop.wallet.model.Wallet;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional
public class JpaWalletAdapter implements WalletRepository {
    private final JpaStores.Wallets wallets;
    private final JpaStores.Transactions transactions;
    private final JpaStores.Disposals disposals;
    private final JpaStores.Redemptions redemptions;
    private final EntityManager entityManager;
    public JpaWalletAdapter(JpaStores.Wallets wallets, JpaStores.Transactions transactions,
            JpaStores.Disposals disposals, JpaStores.Redemptions redemptions, EntityManager entityManager) {
        this.wallets = wallets; this.transactions = transactions; this.disposals = disposals;
        this.redemptions = redemptions; this.entityManager = entityManager;
    }
    @Override public void open(Long userId) {
        entityManager.createNativeQuery("insert into wallets (user_id) values (:user) on conflict (user_id) do nothing")
                .setParameter("user", userId).executeUpdate();
    }
    @Override @Transactional(readOnly = true)
    public Optional<Wallet> findByUserId(Long userId) {
        return wallets.findByUserId(userId).map(row -> new Wallet(row.userId, row.totalEarned, row.totalRedeemed,
                transactions.findByWalletIdOrderByCreatedAtDescIdDesc(row.id).stream().map(t -> new PointTransaction(
                        t.id, t.type, t.points, t.description, t.createdAt, t.disposalId, t.redemptionId)).toList()));
    }
    @Override public void creditDisposal(Long userId, Long disposalId, int points, String item, Instant at) {
        var wallet = requireLocked(userId);
        var disposal = disposals.findById(disposalId).orElseThrow(() -> new IllegalStateException("Descarte ausente."));
        if (!disposal.userId.equals(userId) || disposal.status != DisposalStatus.COMPLETED
                || disposal.awardedPoints != points || transactions.existsByDisposalId(disposalId)) {
            throw new IllegalStateException("Crédito de descarte inválido ou duplicado.");
        }
        var entry = transaction(wallet, PointTransactionType.CREDIT, points, "Descarte de " + item, at);
        entry.disposalId = disposalId;
        wallet.totalEarned = Math.addExact(wallet.totalEarned, points);
        wallet.availablePoints = Math.addExact(wallet.availablePoints, points);
        transactions.saveAndFlush(entry);
    }
    @Override public void debitRedemption(Long userId, Long redemptionId, int points, String reward, Instant at) {
        var wallet = requireLocked(userId);
        var redemption = redemptions.findById(redemptionId).orElseThrow(() -> new IllegalStateException("Resgate ausente."));
        if (!redemption.userId.equals(userId) || redemption.pointsSpent != points
                || transactions.existsByRedemptionId(redemptionId) || points > wallet.availablePoints) {
            throw new IllegalStateException("Débito de resgate inválido ou duplicado.");
        }
        var entry = transaction(wallet, PointTransactionType.DEBIT, points, "Resgate de " + reward, at);
        entry.redemptionId = redemptionId;
        wallet.totalRedeemed = Math.addExact(wallet.totalRedeemed, points);
        wallet.availablePoints -= points;
        transactions.saveAndFlush(entry);
    }
    private WalletRow requireLocked(Long userId) {
        return wallets.lock(userId).orElseThrow(() -> new IllegalStateException("Carteira ausente."));
    }
    private TransactionRow transaction(WalletRow wallet, PointTransactionType type, int points, String text, Instant at) {
        if (points <= 0) throw new IllegalArgumentException("Pontos devem ser positivos.");
        var entry = new TransactionRow();
        entry.walletId = wallet.id; entry.type = type; entry.points = points;
        entry.description = text; entry.createdAt = at;
        return entry;
    }
}
