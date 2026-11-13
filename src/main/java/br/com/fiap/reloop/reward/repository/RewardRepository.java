package br.com.fiap.reloop.reward.repository;

import java.util.List;

import br.com.fiap.reloop.reward.model.Reward;

public interface RewardRepository {

    List<Reward> findAll();
}
