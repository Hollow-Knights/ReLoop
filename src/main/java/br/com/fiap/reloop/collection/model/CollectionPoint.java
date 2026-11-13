package br.com.fiap.reloop.collection.model;

import java.util.List;

public record CollectionPoint(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        boolean active,
        List<Long> acceptedCategoryIds,
        List<Device> devices) {

    public CollectionPoint {
        acceptedCategoryIds = List.copyOf(acceptedCategoryIds);
        devices = List.copyOf(devices);
    }

    public record Device(String deviceId, boolean active) {
    }
}
