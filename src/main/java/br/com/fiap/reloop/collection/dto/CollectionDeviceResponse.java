package br.com.fiap.reloop.collection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CollectionDeviceResponse(String deviceId, @JsonProperty("isActive") boolean active) {
}
