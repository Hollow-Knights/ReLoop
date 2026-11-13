package br.com.fiap.reloop.collection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CollectionPointResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        @JsonProperty("isActive") boolean active) {
}
