package br.com.fiap.reloop.upload.service;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import br.com.fiap.reloop.security.CurrentUserProvider;
import br.com.fiap.reloop.shared.web.ApiException;
import br.com.fiap.reloop.upload.dto.UploadResponse;
import br.com.fiap.reloop.upload.model.UploadedPhoto;
import br.com.fiap.reloop.upload.repository.PhotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Profile("bootstrap")
public class PhotoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhotoService.class);
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int VALIDATION_IMAGE_EDGE = 1024;

    private final PhotoStorage storage;
    private final CurrentUserProvider currentUser;
    private final URI baseUrl;

    public PhotoService(PhotoStorage storage, CurrentUserProvider currentUser,
            @Value("${reloop.photo.base-url}") URI baseUrl) {
        if (!("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getHost() == null || baseUrl.getRawUserInfo() != null
                || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("PHOTO_BASE_URL deve ser uma URL HTTP(S) absoluta, sem credenciais, query ou fragmento.");
        }
        this.storage = storage;
        this.currentUser = currentUser;
        this.baseUrl = baseUrl;
    }

    public UploadResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Envie uma fotografia no campo file.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "A fotografia deve ter até 10 MiB.");
        }
        String contentType = validateImage(file);
        try (var input = file.getInputStream()) {
            UploadedPhoto photo = storage.save(currentUser.userId(), contentType, input);
            return new UploadResponse(photo.id(), urlFor(photo.id()));
        } catch (IOException failure) {
            LOGGER.error("Falha ao armazenar a fotografia", failure);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                    "Não foi possível armazenar a fotografia.");
        }
    }

    public UploadedPhoto requireOwned(UUID id, Long userId) {
        return storage.findById(id).filter(photo -> photo.userId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                        "Fotografia não encontrada."));
    }

    public Download download(UUID id) {
        UploadedPhoto photo = requireOwned(id, currentUser.userId());
        Resource resource = storage.read(photo);
        if (!resource.isReadable()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Fotografia não encontrada.");
        }
        return new Download(resource, MediaType.parseMediaType(photo.contentType()));
    }

    public URI urlFor(UUID id) {
        return UriComponentsBuilder.fromUri(baseUrl)
                .pathSegment("api", "v1", "uploads", id.toString())
                .build(true).toUri();
    }

    private String validateImage(MultipartFile file) {
        try (var input = file.getInputStream(); ImageInputStream image = new MemoryCacheImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                        "Envie uma fotografia JPEG ou PNG.");
            }
            ImageReader reader = readers.next();
            try {
                String contentType = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
                    case "png" -> MediaType.IMAGE_PNG_VALUE;
                    default -> throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                            "Envie uma fotografia JPEG ou PNG.");
                };
                reader.setInput(image, true, true);
                // Decode a bounded raster for validation while preserving the original file.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                var readParams = reader.getDefaultReadParam();
                readParams.setSourceSubsampling(
                        Math.ceilDiv(width, VALIDATION_IMAGE_EDGE), Math.ceilDiv(height, VALIDATION_IMAGE_EDGE), 0, 0);
                reader.read(0, readParams);
                return contentType;
            } finally {
                reader.dispose();
            }
        } catch (IOException failure) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "O conteúdo da fotografia está inválido.");
        }
    }

    public record Download(Resource resource, MediaType contentType) {
    }
}
