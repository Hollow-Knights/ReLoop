package br.com.fiap.reloop.collection.service;

import java.util.List;
import java.util.Optional;

import br.com.fiap.reloop.collection.dto.CollectionDeviceResponse;
import br.com.fiap.reloop.collection.dto.CollectionPointDetailResponse;
import br.com.fiap.reloop.collection.dto.CollectionPointResponse;
import br.com.fiap.reloop.collection.model.CollectionPoint;
import br.com.fiap.reloop.collection.repository.CollectionPointRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class CollectionPointService {

    private final CollectionPointRepository repository;

    public CollectionPointService(CollectionPointRepository repository) {
        this.repository = repository;
    }

    public List<CollectionPointResponse> listPoints() {
        return repository.findAllPoints().stream()
                .map(point -> new CollectionPointResponse(point.id(), point.name(), point.address(),
                        point.latitude(), point.longitude(), point.active()))
                .toList();
    }

    public Optional<CollectionPointDetailResponse> findPoint(Long id) {
        return repository.findPointById(id).map(this::toDetailResponse);
    }

    public Optional<CollectionPoint> findPointByDeviceId(String deviceId) {
        return repository.findAllPoints().stream()
                .filter(point -> point.devices().stream().anyMatch(device -> device.deviceId().equals(deviceId)))
                .findFirst();
    }

    private CollectionPointDetailResponse toDetailResponse(CollectionPoint point) {
        List<CollectionDeviceResponse> devices = point.devices().stream()
                .map(device -> new CollectionDeviceResponse(device.deviceId(), device.active()))
                .toList();
        return new CollectionPointDetailResponse(point.id(), point.name(), point.address(),
                point.latitude(), point.longitude(), point.active(), point.acceptedCategoryIds(), devices);
    }
}
