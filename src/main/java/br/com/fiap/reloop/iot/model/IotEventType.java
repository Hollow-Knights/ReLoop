package br.com.fiap.reloop.iot.model;

import br.com.fiap.reloop.disposal.model.DisposalStatus;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum IotEventType {
    WEIGHT_RECORDED(DisposalStatus.WAITING_WEIGHT),
    DOOR_OPENED(DisposalStatus.WAITING_DEPOSIT),
    DOOR_CLOSED(DisposalStatus.DEPOSIT_IN_PROGRESS);

    private final DisposalStatus expectedStatus;

    IotEventType(DisposalStatus expectedStatus) {
        this.expectedStatus = expectedStatus;
    }

    public DisposalStatus expectedStatus() {
        return expectedStatus;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static IotEventType fromJson(String value) {
        return valueOf(value);
    }
}
