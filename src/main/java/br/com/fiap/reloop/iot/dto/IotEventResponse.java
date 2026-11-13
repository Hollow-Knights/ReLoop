package br.com.fiap.reloop.iot.dto;

import java.math.BigDecimal;

import br.com.fiap.reloop.disposal.model.DisposalStatus;

public record IotEventResponse(
        String eventId,
        Long disposalId,
        DisposalStatus status,
        BigDecimal weightKg,
        Integer estimatedPoints,
        int awardedPoints) {
}
