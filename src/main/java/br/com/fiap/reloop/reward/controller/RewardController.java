package br.com.fiap.reloop.reward.controller;

import java.io.IOException;
import java.util.List;

import br.com.fiap.reloop.reward.dto.RedemptionResponse;
import br.com.fiap.reloop.reward.dto.RewardResponse;
import br.com.fiap.reloop.reward.service.RewardService;
import br.com.fiap.reloop.shared.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Profile("bootstrap")
public class RewardController {

    private final RewardService rewards;

    public RewardController(RewardService rewards) {
        this.rewards = rewards;
    }

    @GetMapping("/rewards")
    public List<RewardResponse> listRewards() {
        return rewards.listRewards();
    }

    @PostMapping("/rewards/{id}/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    public RedemptionResponse redeem(@PathVariable @Positive Long id,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String idempotencyKey,
            HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "O resgate não aceita corpo na requisição.");
        }
        return rewards.redeem(id, idempotencyKey);
    }

    @GetMapping("/users/me/redemptions")
    public List<RedemptionResponse> currentRedemptions() {
        return rewards.currentRedemptions();
    }
}
