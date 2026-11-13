package br.com.fiap.reloop.disposal.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.fiap.reloop.disposal.model.Disposal;

public interface DisposalRepository {

    // Reserva coletor e fotografia na mesma operação que insere o descarte.
    Disposal create(Long userId, String deviceId, Long categoryId, String itemName, UUID photoId);

    Optional<Disposal> findByIdAndUserId(Long id, Long userId);

    List<Disposal> findByUserId(Long userId);

    // A atualização condicional precisa ser atômica para liberar o coletor uma única vez.
    boolean cancelWaitingWeight(Long id, Long userId);

    class CreationConflict extends RuntimeException {

        public enum Reason { PHOTO_ALREADY_LINKED, DEVICE_UNAVAILABLE }

        private final Reason reason;

        public CreationConflict(Reason reason) {
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }
}
