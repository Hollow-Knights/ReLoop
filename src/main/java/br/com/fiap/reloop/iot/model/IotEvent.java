package br.com.fiap.reloop.iot.model;

import java.math.BigDecimal;

public record IotEvent(String eventId, String deviceId, Long disposalId, IotEventType eventType, BigDecimal weightKg) {

    public boolean hasSamePayload(IotEvent other) {
        return deviceId.equals(other.deviceId) && disposalId.equals(other.disposalId)
                && eventType == other.eventType
                && (weightKg == null ? other.weightKg == null
                        : other.weightKg != null && weightKg.compareTo(other.weightKg) == 0);
    }
}
