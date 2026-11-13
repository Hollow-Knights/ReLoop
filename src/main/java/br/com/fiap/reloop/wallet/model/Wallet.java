package br.com.fiap.reloop.wallet.model;

import java.util.ArrayList;
import java.util.List;

public record Wallet(Long userId, int totalEarned, int totalRedeemed, List<PointTransaction> transactions) {

    public Wallet {
        transactions = List.copyOf(transactions);
    }

    public int availablePoints() {
        return totalEarned - totalRedeemed;
    }

    public Wallet withTransaction(PointTransaction transaction) {
        int earned = totalEarned;
        int redeemed = totalRedeemed;
        switch (transaction.type()) {
            case CREDIT -> earned = Math.addExact(earned, transaction.points());
            case DEBIT -> {
                if (transaction.points() > availablePoints()) {
                    throw new IllegalStateException("O débito excede o saldo da carteira.");
                }
                redeemed += transaction.points();
            }
        }
        var updated = new ArrayList<>(transactions);
        updated.add(transaction);
        return new Wallet(userId, earned, redeemed, updated);
    }
}
