package br.com.fiap.reloop.upload.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import br.com.fiap.reloop.upload.model.UploadedPhoto;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

@Repository
@Profile("bootstrap")
public class TemporaryPhotoStorage implements PhotoStorage {

    private final Path directory;
    private final Map<UUID, UploadedPhoto> photos = new ConcurrentHashMap<>();

    public TemporaryPhotoStorage() throws IOException {
        directory = Files.createTempDirectory("reloop-photos-");
    }

    @Override
    public UploadedPhoto save(Long userId, String contentType, InputStream input) throws IOException {
        while (true) {
            UUID id = UUID.randomUUID();
            Path path = directory.resolve(id.toString());
            OutputStream file;
            try {
                file = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException collision) {
                continue;
            }
            try (file) {
                input.transferTo(file);
            } catch (IOException failure) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            UploadedPhoto photo = new UploadedPhoto(id, userId, contentType);
            photos.put(id, photo);
            return photo;
        }
    }

    @Override
    public Optional<UploadedPhoto> findById(UUID id) {
        return Optional.ofNullable(photos.get(id));
    }

    @Override
    public Resource read(UploadedPhoto photo) {
        return new FileSystemResource(directory.resolve(photo.id().toString()));
    }

    @PreDestroy
    void cleanUp() throws IOException {
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                Files.deleteIfExists(entry);
            }
        }
        Files.deleteIfExists(directory);
    }
}
