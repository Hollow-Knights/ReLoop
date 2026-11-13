package br.com.fiap.reloop.wallet.controller;

import br.com.fiap.reloop.wallet.dto.WalletResponse;
import br.com.fiap.reloop.wallet.service.WalletService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("bootstrap")
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/api/v1/users/me/wallet")
    public WalletResponse currentWallet() {
        return wallets.currentWallet();
    }
}
