package br.com.fiap.reloop.iot.repository;

import java.util.Optional;
import java.util.function.UnaryOperator;

import br.com.fiap.reloop.disposal.model.Disposal;
import br.com.fiap.reloop.iot.model.IotEvent;

public interface IotEventRepository {

    /**
     * Resolve a repetição antes de consultar o descarte. Uma nova transição deve guardar o
     * evento, seu resultado e eventual crédito em uma única operação, sem gravação se falhar.
     * Um descarte fora do escopo do coletor produz um resultado vazio.
     */
    Optional<Disposal> process(IotEvent event, UnaryOperator<Disposal> transition);

    class EventIdConflict extends RuntimeException {
    }
}
