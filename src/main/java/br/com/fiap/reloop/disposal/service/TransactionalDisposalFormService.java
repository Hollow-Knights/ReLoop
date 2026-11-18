package br.com.fiap.reloop.disposal.service;

import br.com.fiap.reloop.disposal.dto.CreateDisposalFormRequest;
import br.com.fiap.reloop.disposal.dto.DisposalResponse;
import br.com.fiap.reloop.upload.service.PhotoService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("postgres")
public class TransactionalDisposalFormService implements DisposalFormService {
    private final PhotoService photos;
    private final DisposalService disposals;

    public TransactionalDisposalFormService(PhotoService photos, DisposalService disposals) {
        this.photos = photos;
        this.disposals = disposals;
    }

    // Called through a Spring proxy. JpaPhotoStorage joins this transaction and owns file completion callbacks.
    @Override @Transactional
    public DisposalResponse create(CreateDisposalFormRequest request) {
        var photo = photos.upload(request.file());
        return disposals.create(request.withPhoto(photo.photoId()));
    }
}
