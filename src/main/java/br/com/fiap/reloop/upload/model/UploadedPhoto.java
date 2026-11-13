package br.com.fiap.reloop.upload.model;

import java.util.UUID;

public record UploadedPhoto(UUID id, Long userId, String contentType) {
}
