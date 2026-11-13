package br.com.fiap.reloop.disposal.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import br.com.fiap.reloop.disposal.model.Disposal;
import br.com.fiap.reloop.disposal.model.DisposalStatus;
import br.com.fiap.reloop.iot.model.IotEvent;
import br.com.fiap.reloop.iot.repository.IotEventRepository;
import br.com.fiap.reloop.shared.persistence.InMemoryPointsLock;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("bootstrap")
public class InMemoryDisposalRepository implements DisposalRepository, IotEventRepository {

    private final InMemoryPointsLock pointsLock;
    private final WalletRepository wallets;
    private final Map<Long, Disposal> disposals = new HashMap<>();
    private final Map<String, ProcessedEvent> events = new HashMap<>();
    private long nextId = 1;

    public InMemoryDisposalRepository(InMemoryPointsLock pointsLock, WalletRepository wallets) {
        this.pointsLock = pointsLock;
        this.wallets = wallets;
    }

    @Override
    public synchronized Disposal create(Long userId, String deviceId, Long categoryId, String itemName, UUID photoId) {
        if (disposals.values().stream().anyMatch(disposal -> disposal.photoId().equals(photoId))) {
            throw new CreationConflict(CreationConflict.Reason.PHOTO_ALREADY_LINKED);
        }
        if (disposals.values().stream().anyMatch(disposal ->
                disposal.deviceId().equals(deviceId) && disposal.status().isActive())) {
            throw new CreationConflict(CreationConflict.Reason.DEVICE_UNAVAILABLE);
        }
        Disposal disposal = Disposal.start(nextId++, userId, deviceId, categoryId, itemName, photoId, Instant.now());
        disposals.put(disposal.id(), disposal);
        return disposal;
    }

    @Override
    public synchronized Optional<Disposal> findByIdAndUserId(Long id, Long userId) {
        return Optional.ofNullable(disposals.get(id)).filter(disposal -> disposal.userId().equals(userId));
    }

    @Override
    public synchronized List<Disposal> findByUserId(Long userId) {
        return disposals.values().stream().filter(disposal -> disposal.userId().equals(userId)).toList();
    }

    @Override
    public synchronized boolean cancelWaitingWeight(Long id, Long userId) {
        Disposal disposal = disposals.get(id);
        if (disposal == null || !disposal.userId().equals(userId) || disposal.status() != DisposalStatus.WAITING_WEIGHT) {
            return false;
        }
        disposals.put(id, disposal.cancel());
        return true;
    }

    @Override
    public synchronized Optional<Disposal> process(IotEvent event, UnaryOperator<Disposal> transition) {
        ProcessedEvent previous = events.get(event.eventId());
        if (previous != null) {
            if (!previous.event().hasSamePayload(event)) {
                throw new EventIdConflict();
            }
            return Optional.of(previous.result());
        }

        Disposal disposal = disposals.get(event.disposalId());
        if (disposal == null || !disposal.deviceId().equals(event.deviceId())) {
            return Optional.empty();
        }

        Disposal result = transition.apply(disposal);
        synchronized (pointsLock) {
            if (result.status() == DisposalStatus.COMPLETED && result.awardedPoints() > 0) {
                wallets.creditDisposal(result.userId(), result.id(), result.awardedPoints(), result.itemName(),
                        Instant.now());
            }
            disposals.put(result.id(), result);
            events.put(event.eventId(), new ProcessedEvent(event, result));
        }
        return Optional.of(result);
    }

    private record ProcessedEvent(IotEvent event, Disposal result) {
    }
}
