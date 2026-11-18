package br.com.fiap.reloop.disposal.service;

import br.com.fiap.reloop.disposal.dto.CreateDisposalFormRequest;
import br.com.fiap.reloop.disposal.dto.DisposalResponse;
import br.com.fiap.reloop.disposal.repository.InMemoryDisposalRepository;
import br.com.fiap.reloop.upload.repository.TemporaryPhotoStorage;
import br.com.fiap.reloop.upload.service.PhotoService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class BootstrapDisposalFormService implements DisposalFormService {
    private final PhotoService photos;
    private final DisposalService disposals;
    private final TemporaryPhotoStorage storage;
    private final InMemoryDisposalRepository repository;

    public BootstrapDisposalFormService(PhotoService photos, DisposalService disposals, TemporaryPhotoStorage storage,
            InMemoryDisposalRepository repository) {
        this.photos = photos;
        this.disposals = disposals;
        this.storage = storage;
        this.repository = repository;
    }

    @Override public DisposalResponse create(CreateDisposalFormRequest request) {
        return repository.withRollback(() -> storage.withRollback(() -> {
            var photo = photos.upload(request.file());
            return disposals.create(request.withPhoto(photo.photoId()));
        }));
    }
}
