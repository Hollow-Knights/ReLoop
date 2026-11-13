package br.com.fiap.reloop.disposal.service;

import java.util.Comparator;
import java.util.List;

import br.com.fiap.reloop.collection.model.CollectionPoint;
import br.com.fiap.reloop.collection.service.CollectionPointService;
import br.com.fiap.reloop.collection.service.ElectronicCategoryService;
import br.com.fiap.reloop.disposal.dto.CreateDisposalRequest;
import br.com.fiap.reloop.disposal.dto.DisposalHistoryResponse;
import br.com.fiap.reloop.disposal.dto.DisposalResponse;
import br.com.fiap.reloop.disposal.model.Disposal;
import br.com.fiap.reloop.disposal.repository.DisposalRepository;
import br.com.fiap.reloop.security.CurrentUserProvider;
import br.com.fiap.reloop.shared.web.ApiException;
import br.com.fiap.reloop.upload.service.PhotoService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class DisposalService {

    private final DisposalRepository repository;
    private final CollectionPointService collectionPoints;
    private final ElectronicCategoryService categories;
    private final PhotoService photos;
    private final CurrentUserProvider currentUser;

    public DisposalService(DisposalRepository repository, CollectionPointService collectionPoints,
            ElectronicCategoryService categories, PhotoService photos, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.collectionPoints = collectionPoints;
        this.categories = categories;
        this.photos = photos;
        this.currentUser = currentUser;
    }

    public DisposalResponse create(CreateDisposalRequest request) {
        Long userId = currentUser.userId();
        CollectionPoint point = collectionPoints.findPointByDeviceId(request.deviceId())
                .orElseThrow(() -> notFound("Coletor não encontrado."));
        if (!categories.existsById(request.categoryId())) {
            throw notFound("Categoria não encontrada.");
        }
        boolean deviceActive = point.devices().stream()
                .anyMatch(device -> device.deviceId().equals(request.deviceId()) && device.active());
        if (!point.active() || !deviceActive) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_UNAVAILABLE", "Coletor ou ponto de coleta inativo.");
        }
        if (!point.acceptedCategoryIds().contains(request.categoryId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CATEGORY_NOT_ACCEPTED",
                    "O ponto de coleta não aceita a categoria.");
        }
        var photo = photos.requireOwned(request.photoId(), userId);
        try {
            Disposal disposal = repository.create(
                    userId, request.deviceId(), request.categoryId(), request.itemName(), photo.id());
            return toResponse(disposal);
        } catch (DisposalRepository.CreationConflict conflict) {
            throw switch (conflict.reason()) {
                case PHOTO_ALREADY_LINKED -> new ApiException(HttpStatus.CONFLICT, "PHOTO_ALREADY_LINKED",
                        "A fotografia já está associada a um descarte.");
                case DEVICE_UNAVAILABLE -> new ApiException(HttpStatus.CONFLICT, "DEVICE_UNAVAILABLE",
                        "O coletor já tem um descarte ativo.");
            };
        }
    }

    public DisposalResponse find(Long id) {
        return toResponse(requireOwned(id, currentUser.userId()));
    }

    public void cancel(Long id) {
        Long userId = currentUser.userId();
        requireOwned(id, userId);
        if (!repository.cancelWaitingWeight(id, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_DISPOSAL_STATE",
                    "Somente um descarte que aguarda pesagem pode ser cancelado.");
        }
    }

    public List<DisposalHistoryResponse> history() {
        return repository.findByUserId(currentUser.userId()).stream()
                .sorted(Comparator.comparing(Disposal::createdAt).thenComparing(Disposal::id).reversed())
                .map(disposal -> new DisposalHistoryResponse(disposal.id(), disposal.itemName(), disposal.categoryId(),
                        disposal.status(), disposal.weightKg(), disposal.awardedPoints(), disposal.createdAt()))
                .toList();
    }

    private Disposal requireOwned(Long id, Long userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> notFound("Descarte não encontrado."));
    }

    private DisposalResponse toResponse(Disposal disposal) {
        return new DisposalResponse(disposal.id(), disposal.deviceId(), disposal.categoryId(), disposal.itemName(),
                disposal.photoId(), photos.urlFor(disposal.photoId()), disposal.status(), disposal.weightKg(),
                disposal.estimatedPoints(), disposal.awardedPoints(), disposal.createdAt());
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}
