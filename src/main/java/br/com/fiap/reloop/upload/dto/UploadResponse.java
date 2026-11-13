package br.com.fiap.reloop.upload.dto;

import java.net.URI;
import java.util.UUID;

public record UploadResponse(UUID photoId, URI photoUrl) {
}
