package br.com.fiap.reloop.wallet.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.com.fiap.reloop.shared.persistence.InMemoryPointsLock;
import br.com.fiap.reloop.wallet.model.PointTransaction;
import br.com.fiap.reloop.wallet.model.PointTransactionType;
import br.com.fiap.reloop.wallet.model.Wallet;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("bootstrap")
public class InMemoryWalletRepository implements WalletRepository {

    private final InMemoryPointsLock lock;
    private final Map<Long, Wallet> wallets = new HashMap<>();
    private final Set<Long> creditedDisposalIds = new HashSet<>();
    private long nextTransactionId = 1L;

    public InMemoryWalletRepository(InMemoryPointsLock lock) {
        this.lock = lock;
    }

    @Override
    public void open(Long userId) {
        synchronized (lock) {
            wallets.putIfAbsent(userId, new Wallet(userId, 0, 0, List.of()));
        }
    }

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        synchronized (lock) {
            return Optional.ofNullable(wallets.get(userId));
        }
    }

    @Override
    public void creditDisposal(Long userId, Long disposalId, int points, String itemName, Instant createdAt) {
        synchronized (lock) {
            if (creditedDisposalIds.contains(disposalId)) {
                throw new IllegalStateException("O descarte já possui um crédito.");
            }
            append(userId, new PointTransaction(nextTransactionId, PointTransactionType.CREDIT, points,
                    "Descarte de " + itemName, createdAt, disposalId, null));
            creditedDisposalIds.add(disposalId);
        }
    }

    @Override
    public void debitRedemption(Long userId, Long redemptionId, int points, String rewardName, Instant createdAt) {
        synchronized (lock) {
            append(userId, new PointTransaction(nextTransactionId, PointTransactionType.DEBIT, points,
                    "Resgate de " + rewardName, createdAt, null, redemptionId));
        }
    }

    private void append(Long userId, PointTransaction transaction) {
        Wallet wallet = wallets.get(userId);
        if (wallet == null) {
            throw new IllegalStateException("Carteira não encontrada.");
        }
        wallets.put(userId, wallet.withTransaction(transaction));
        nextTransactionId++;
    }
}
