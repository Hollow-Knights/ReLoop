package br.com.fiap.reloop.persistence.postgres;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.FileRow;
import br.com.fiap.reloop.upload.model.UploadedPhoto;
import br.com.fiap.reloop.upload.repository.PhotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("postgres")
public class JpaPhotoStorage implements PhotoStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(JpaPhotoStorage.class);
    private final JpaStores.Files files;
    private final Path root;
    public JpaPhotoStorage(JpaStores.Files files, @Value("${reloop.photo.storage-dir}") Path directory) throws IOException {
        this.files = files;
        Files.createDirectories(directory);
        root = directory.toRealPath();
    }
    @Override @Transactional(rollbackFor = IOException.class)
    public UploadedPhoto save(Long userId, String contentType, InputStream input) throws IOException {
        while (true) {
            UUID id = UUID.randomUUID();
            String key = "photos/" + userId + "/" + id;
            Path path = resolve(key);
            Files.createDirectories(path.getParent());
            // Check again after creating the parent; symbolic links are never valid storage keys.
            path = resolve(key);
            java.io.OutputStream output;
            try { output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
            catch (FileAlreadyExistsException collision) { continue; }
            Path newFile = path;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try { Files.deleteIfExists(newFile); }
                        catch (IOException failure) { LOGGER.error("Não foi possível remover upload revertido", failure); }
                    } else if (status == STATUS_UNKNOWN) {
                        LOGGER.warn("Resultado transacional desconhecido para upload {}; arquivo preservado. Reconciliação necessária.", id);
                    }
                }
            });
            long size;
            try (output) { size = input.transferTo(output); }
            var row = new FileRow();
            row.id = id; row.userId = userId; row.storageKey = key; row.contentType = contentType;
            row.sizeBytes = size; row.createdAt = DatabaseTime.now();
            files.saveAndFlush(row);
            return domain(row);
        }
    }
    @Override @Transactional(readOnly = true)
    public Optional<UploadedPhoto> findById(UUID id) { return files.findById(id).map(JpaPhotoStorage::domain); }
    @Override @Transactional(readOnly = true)
    public Resource read(UploadedPhoto photo) {
        var row = files.findById(photo.id()).filter(f -> f.userId.equals(photo.userId())).orElseThrow();
        return new FileSystemResource(resolve(row.storageKey));
    }
    private Path resolve(String key) {
        Path candidate = root.resolve(key).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) throw new IllegalStateException("Chave de armazenamento inválida.");
        Path part = root;
        for (Path component : root.relativize(candidate)) {
            part = part.resolve(component);
            if (Files.isSymbolicLink(part)) throw new IllegalStateException("Link simbólico não permitido no armazenamento.");
        }
        return candidate;
    }
    private static UploadedPhoto domain(FileRow row) { return new UploadedPhoto(row.id, row.userId, row.contentType); }
}
