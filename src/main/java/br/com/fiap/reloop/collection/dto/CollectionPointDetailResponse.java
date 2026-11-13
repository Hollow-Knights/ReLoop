package br.com.fiap.reloop.collection.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CollectionPointDetailResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        @JsonProperty("isActive") boolean active,
        List<Long> acceptedCategoryIds,
        List<CollectionDeviceResponse> devices) {
}
