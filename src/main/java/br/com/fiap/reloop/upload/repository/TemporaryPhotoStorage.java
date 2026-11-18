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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryPhotoStorage.class);
    private final Path directory;
    private final Map<UUID, UploadedPhoto> photos = new ConcurrentHashMap<>();
    private final ThreadLocal<List<UUID>> pendingUploads = new ThreadLocal<>();

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
            } catch (IOException | RuntimeException failure) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            UploadedPhoto photo = new UploadedPhoto(id, userId, contentType);
            photos.put(id, photo);
            var pending = pendingUploads.get();
            if (pending != null) pending.add(id);
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

    /** Compensation for the bootstrap form only, including failures while closing an already-saved input. */
    public <T> T withRollback(Supplier<T> work) {
        if (pendingUploads.get() != null) throw new IllegalStateException("Escopo de upload já aberto.");
        var pending = new ArrayList<UUID>();
        pendingUploads.set(pending);
        try {
            return work.get();
        } catch (RuntimeException | Error failure) {
            for (UUID id : pending) {
                try {
                    Files.deleteIfExists(directory.resolve(id.toString()));
                    photos.remove(id);
                } catch (IOException cleanupFailure) {
                    // Keep the metadata if deletion failed so the remaining file can be reconciled.
                    LOGGER.error("Não foi possível remover upload revertido {}; reconciliação necessária.", id, cleanupFailure);
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        } finally {
            pendingUploads.remove();
        }
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
