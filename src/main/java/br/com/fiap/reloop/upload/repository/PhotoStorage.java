package br.com.fiap.reloop.upload.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import br.com.fiap.reloop.upload.model.UploadedPhoto;
import org.springframework.core.io.Resource;

public interface PhotoStorage {

    UploadedPhoto save(Long userId, String contentType, InputStream input) throws IOException;

    Optional<UploadedPhoto> findById(UUID id);

    Resource read(UploadedPhoto photo);
}
