package br.com.fiap.reloop.disposal.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateDisposalRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 120) String itemName,
        @NotNull UUID photoId) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Campo não previsto no cadastro de descarte: " + name);
    }
}
