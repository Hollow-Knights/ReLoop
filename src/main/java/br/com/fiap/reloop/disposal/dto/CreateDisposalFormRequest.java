package br.com.fiap.reloop.disposal.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record CreateDisposalFormRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 120) String itemName,
        @NotNull MultipartFile file) {

    public CreateDisposalRequest withPhoto(UUID photoId) {
        return new CreateDisposalRequest(deviceId, categoryId, itemName, photoId);
    }
}
