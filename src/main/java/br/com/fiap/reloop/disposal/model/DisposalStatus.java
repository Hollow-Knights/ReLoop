package br.com.fiap.reloop.disposal.model;

public enum DisposalStatus {
    WAITING_WEIGHT,
    WAITING_DEPOSIT,
    DEPOSIT_IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isActive() {
        return this == WAITING_WEIGHT || this == WAITING_DEPOSIT || this == DEPOSIT_IN_PROGRESS;
    }
}
