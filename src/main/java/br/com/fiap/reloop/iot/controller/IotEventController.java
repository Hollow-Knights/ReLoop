package br.com.fiap.reloop.iot.controller;

import br.com.fiap.reloop.iot.dto.IotEventRequest;
import br.com.fiap.reloop.iot.dto.IotEventResponse;
import br.com.fiap.reloop.iot.security.DeviceKeyFilter;
import br.com.fiap.reloop.iot.service.IotEventService;
import br.com.fiap.reloop.shared.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("bootstrap")
public class IotEventController {

    private final IotEventService events;

    public IotEventController(IotEventService events) {
        this.events = events;
    }

    @PostMapping(path = "/api/v1/iot/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IotEventResponse receive(@RequestAttribute(DeviceKeyFilter.DEVICE_ID_ATTRIBUTE) String deviceId,
            @Valid @RequestBody IotEventRequest request) {
        if (!deviceId.equals(request.deviceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEVICE_FORBIDDEN",
                    "A credencial não autoriza o coletor informado.");
        }
        return events.receive(request);
    }
}
