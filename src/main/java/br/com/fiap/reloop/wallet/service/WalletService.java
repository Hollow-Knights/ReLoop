package br.com.fiap.reloop.wallet.service;

import java.util.Comparator;

import br.com.fiap.reloop.security.CurrentUserProvider;
import br.com.fiap.reloop.shared.web.ApiException;
import br.com.fiap.reloop.wallet.dto.PointTransactionResponse;
import br.com.fiap.reloop.wallet.dto.WalletResponse;
import br.com.fiap.reloop.wallet.model.PointTransaction;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class WalletService {

    private final WalletRepository wallets;
    private final CurrentUserProvider currentUser;

    public WalletService(WalletRepository wallets, CurrentUserProvider currentUser) {
        this.wallets = wallets;
        this.currentUser = currentUser;
    }

    public WalletResponse currentWallet() {
        var wallet = wallets.findByUserId(currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                        "Identidade do usuário indisponível."));
        var transactions = wallet.transactions().stream()
                .sorted(Comparator.comparing(PointTransaction::createdAt).thenComparingLong(PointTransaction::id).reversed())
                .map(PointTransactionResponse::from)
                .toList();
        return new WalletResponse(wallet.availablePoints(), wallet.totalEarned(), wallet.totalRedeemed(), transactions);
    }
}
