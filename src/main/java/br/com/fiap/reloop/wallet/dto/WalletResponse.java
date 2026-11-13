package br.com.fiap.reloop.wallet.dto;

import java.util.List;

public record WalletResponse(
        int availablePoints, int totalEarned, int totalRedeemed, List<PointTransactionResponse> transactions) {
}
