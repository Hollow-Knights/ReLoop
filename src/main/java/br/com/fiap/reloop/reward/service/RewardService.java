package br.com.fiap.reloop.reward.service;

import java.util.Comparator;
import java.util.List;

import br.com.fiap.reloop.reward.dto.RedemptionResponse;
import br.com.fiap.reloop.reward.dto.RewardResponse;
import br.com.fiap.reloop.reward.model.Redemption;
import br.com.fiap.reloop.reward.repository.RedemptionRepository;
import br.com.fiap.reloop.reward.repository.RewardRepository;
import br.com.fiap.reloop.security.CurrentUserProvider;
import br.com.fiap.reloop.shared.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class RewardService {

    private final RewardRepository rewards;
    private final RedemptionRepository redemptions;
    private final CurrentUserProvider currentUser;

    public RewardService(RewardRepository rewards, RedemptionRepository redemptions, CurrentUserProvider currentUser) {
        this.rewards = rewards;
        this.redemptions = redemptions;
        this.currentUser = currentUser;
    }

    public List<RewardResponse> listRewards() {
        return rewards.findAll().stream().map(RewardResponse::from).toList();
    }

    public RedemptionResponse redeem(Long rewardId, String idempotencyKey) {
        try {
            return RedemptionResponse.from(redemptions.redeem(currentUser.userId(), rewardId, idempotencyKey));
        } catch (RedemptionRepository.RedemptionRejected rejected) {
            throw switch (rejected.reason()) {
                case REWARD_NOT_FOUND -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                        "Benefício não encontrado.");
                case REWARD_UNAVAILABLE -> new ApiException(HttpStatus.CONFLICT, "REWARD_UNAVAILABLE",
                        "O benefício está inativo ou esgotado.");
                case INSUFFICIENT_POINTS -> new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_POINTS",
                        "Saldo insuficiente para resgatar o benefício.");
                case IDEMPOTENCY_KEY_CONFLICT -> new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "A chave já foi usada para resgatar outro benefício.");
                case WALLET_NOT_FOUND -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                        "Identidade do usuário indisponível.");
            };
        }
    }

    public List<RedemptionResponse> currentRedemptions() {
        return redemptions.findByUserId(currentUser.userId()).stream()
                .sorted(Comparator.comparing(Redemption::redeemedAt).thenComparing(Redemption::id).reversed())
                .map(RedemptionResponse::from)
                .toList();
    }
}
