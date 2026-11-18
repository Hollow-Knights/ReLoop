package br.com.fiap.reloop.persistence.postgres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import br.com.fiap.reloop.disposal.model.Disposal;
import br.com.fiap.reloop.disposal.model.DisposalStatus;
import br.com.fiap.reloop.disposal.repository.DisposalRepository;
import br.com.fiap.reloop.iot.dto.IotEventResponse;
import br.com.fiap.reloop.iot.model.IotEvent;
import br.com.fiap.reloop.iot.repository.IotEventRepository;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.*;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional
public class JpaDisposalAdapter implements DisposalRepository, IotEventRepository {
    private final JpaStores.Disposals disposals;
    private final JpaStores.Devices devices;
    private final JpaStores.Events events;
    private final WalletRepository wallets;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    public JpaDisposalAdapter(JpaStores.Disposals disposals, JpaStores.Devices devices, JpaStores.Events events,
            WalletRepository wallets, EntityManager entityManager, ObjectMapper mapper) {
        this.disposals = disposals; this.devices = devices; this.events = events;
        this.wallets = wallets; this.entityManager = entityManager; this.mapper = mapper;
    }
    @Override public Disposal create(Long userId, String deviceId, Long categoryId, String name, UUID photoId) {
        var device = devices.lock(deviceId).orElseThrow(() -> new IllegalStateException("Coletor ausente."));
        if (disposals.existsByPhotoId(photoId)) throw new CreationConflict(CreationConflict.Reason.PHOTO_ALREADY_LINKED);
        if (!device.active || disposals.active(deviceId)) throw new CreationConflict(CreationConflict.Reason.DEVICE_UNAVAILABLE);
        var row = new DisposalRow();
        row.userId = userId; row.deviceId = deviceId; row.categoryId = categoryId; row.itemName = name; row.photoId = photoId;
        row.status = DisposalStatus.WAITING_WEIGHT; row.awardedPoints = 0; row.createdAt = row.updatedAt = DatabaseTime.now();
        try { disposals.saveAndFlush(row); }
        catch (RuntimeException failure) {
            if (SqlConflicts.causedBy(failure, "disposals_photo_id_key"))
                throw new CreationConflict(CreationConflict.Reason.PHOTO_ALREADY_LINKED);
            if (SqlConflicts.causedBy(failure, "ux_disposals_active_device"))
                throw new CreationConflict(CreationConflict.Reason.DEVICE_UNAVAILABLE);
            throw failure;
        }
        return domain(row);
    }
    @Override @Transactional(readOnly = true)
    public Optional<Disposal> findByIdAndUserId(Long id, Long userId) {
        return disposals.findByIdAndUserId(id, userId).map(JpaDisposalAdapter::domain);
    }
    @Override @Transactional(readOnly = true)
    public List<Disposal> findByUserId(Long userId) {
        return disposals.findByUserId(userId).stream().map(JpaDisposalAdapter::domain).toList();
    }
    @Override public boolean cancelWaitingWeight(Long id, Long userId) {
        var row = disposals.lock(id).orElse(null);
        if (row == null || !row.userId.equals(userId) || row.status != DisposalStatus.WAITING_WEIGHT) return false;
        row.status = DisposalStatus.CANCELLED; row.updatedAt = DatabaseTime.now();
        disposals.flush();
        return true;
    }
    @Override public Optional<Disposal> process(IotEvent event, UnaryOperator<Disposal> transition) {
        // The event may not have a row yet. A database transaction lock serializes its key across instances.
        // Hash collisions only serialize unrelated keys; the primary key and full payload remain authoritative.
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:event, 0))")
                .setParameter("event", event.eventId()).getSingleResult();
        var previous = events.findById(event.eventId());
        if (previous.isPresent()) {
            var row = previous.get();
            if (!new IotEvent(row.eventId, row.deviceId, row.disposalId, row.eventType, row.weightKg).hasSamePayload(event))
                throw new EventIdConflict();
            var original = disposals.findById(row.disposalId).orElseThrow();
            return Optional.of(snapshot(original, row.responsePayload));
        }
        var row = disposals.lock(event.disposalId()).orElse(null);
        if (row == null || !row.deviceId.equals(event.deviceId())) return Optional.empty();
        Disposal result = transition.apply(domain(row));
        row.status = result.status(); row.weightKg = result.weightKg(); row.estimatedPoints = result.estimatedPoints();
        row.awardedPoints = result.awardedPoints(); row.updatedAt = DatabaseTime.now();
        row.completedAt = result.status() == DisposalStatus.COMPLETED ? row.updatedAt : null;
        disposals.flush();
        if (result.status() == DisposalStatus.COMPLETED && result.awardedPoints() > 0) {
            wallets.creditDisposal(result.userId(), result.id(), result.awardedPoints(), result.itemName(), row.updatedAt);
        }
        var accepted = new EventRow();
        accepted.eventId = event.eventId(); accepted.deviceId = event.deviceId(); accepted.disposalId = event.disposalId();
        accepted.eventType = event.eventType(); accepted.weightKg = event.weightKg(); accepted.processedAt = row.updatedAt;
        accepted.responsePayload = mapper.valueToTree(new IotEventResponse(event.eventId(), result.id(), result.status(),
                result.weightKg(), result.estimatedPoints(), result.awardedPoints()));
        events.saveAndFlush(accepted);
        return Optional.of(result);
    }
    private static Disposal domain(DisposalRow r) {
        return new Disposal(r.id, r.userId, r.deviceId, r.categoryId, r.itemName, r.photoId, r.status,
                r.weightKg, r.estimatedPoints, r.awardedPoints, r.createdAt);
    }
    private static Disposal snapshot(DisposalRow r, JsonNode payload) {
        var status = DisposalStatus.valueOf(payload.get("status").asText());
        // Hiago's demo events contain partial responses. Immutable disposal values fill their missing fields.
        var weight = payload.has("weightKg") ? (payload.get("weightKg").isNull() ? null : payload.get("weightKg").decimalValue()) : r.weightKg;
        Integer estimate = payload.has("estimatedPoints") ? (payload.get("estimatedPoints").isNull() ? null : payload.get("estimatedPoints").intValue()) : r.estimatedPoints;
        int awarded = payload.has("awardedPoints") ? payload.get("awardedPoints").intValue()
                : status == DisposalStatus.COMPLETED ? r.awardedPoints : 0;
        return new Disposal(r.id, r.userId, r.deviceId, r.categoryId, r.itemName, r.photoId,
                status, weight, estimate, awarded, r.createdAt);
    }
}
