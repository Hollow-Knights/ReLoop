package br.com.fiap.reloop.iot.service;

import br.com.fiap.reloop.collection.service.ElectronicCategoryService;
import br.com.fiap.reloop.disposal.model.Disposal;
import br.com.fiap.reloop.disposal.service.PointCalculationService;
import br.com.fiap.reloop.iot.dto.IotEventRequest;
import br.com.fiap.reloop.iot.dto.IotEventResponse;
import br.com.fiap.reloop.iot.model.IotEvent;
import br.com.fiap.reloop.iot.repository.IotEventRepository;
import br.com.fiap.reloop.shared.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class IotEventService {

    private final IotEventRepository events;
    private final ElectronicCategoryService categories;
    private final PointCalculationService points;

    public IotEventService(IotEventRepository events, ElectronicCategoryService categories,
            PointCalculationService points) {
        this.events = events;
        this.categories = categories;
        this.points = points;
    }

    public IotEventResponse receive(IotEventRequest request) {
        var event = new IotEvent(request.eventId(), request.deviceId(), request.disposalId(),
                request.eventType(), request.weightKg());
        try {
            Disposal result = events.process(event, disposal -> apply(event, disposal))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                            "Descarte indisponível para o coletor."));
            return new IotEventResponse(event.eventId(), result.id(), result.status(), result.weightKg(),
                    result.estimatedPoints(), result.awardedPoints());
        } catch (IotEventRepository.EventIdConflict conflict) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_ID_CONFLICT",
                    "O identificador já foi usado com outro conteúdo.");
        }
    }

    private Disposal apply(IotEvent event, Disposal disposal) {
        if (disposal.status() != event.eventType().expectedStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_DISPOSAL_STATE",
                    "O estado atual do descarte não permite este evento.");
        }
        return switch (event.eventType()) {
            case WEIGHT_RECORDED -> {
                var category = categories.findCategoryById(disposal.categoryId())
                        .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                                "A categoria do descarte não está disponível."));
                yield disposal.recordWeight(event.weightKg(), points.calculate(category, event.weightKg()));
            }
            case DOOR_OPENED -> disposal.openDoor();
            case DOOR_CLOSED -> disposal.complete();
        };
    }
}
