package br.com.fiap.reloop.upload.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;
import br.com.fiap.reloop.upload.model.UploadedPhoto;
import org.junit.jupiter.api.Test;

class TemporaryPhotoScopeTest {
    @Test void failureAfterMemoryInsertRevertsOnlyTheNewDisposalAndPhoto() throws Exception {
        var storage = new TemporaryPhotoStorage();
        try {
            var repository = new br.com.fiap.reloop.disposal.repository.InMemoryDisposalRepository(
                    new br.com.fiap.reloop.shared.persistence.InMemoryPointsLock(),
                    org.mockito.Mockito.mock(br.com.fiap.reloop.wallet.repository.WalletRepository.class));
            var oldPhoto = storage.save(99L, "image/png", new ByteArrayInputStream(new byte[]{1}));
            var oldDisposal = repository.create(99L, "COL-003", 1L, "Anterior", oldPhoto.id());
            var newId = new AtomicReference<java.util.UUID>();
            var disposals = org.mockito.Mockito.mock(br.com.fiap.reloop.disposal.service.DisposalService.class);
            org.mockito.Mockito.doAnswer(invocation -> {
                var request = invocation.<br.com.fiap.reloop.disposal.dto.CreateDisposalRequest>getArgument(0);
                newId.set(request.photoId());
                repository.create(42L, request.deviceId(), request.categoryId(), request.itemName(), request.photoId());
                throw new IllegalStateException("failure after insert");
            }).when(disposals).create(org.mockito.ArgumentMatchers.any());
            var photos = new br.com.fiap.reloop.upload.service.PhotoService(storage, () -> 42L, java.net.URI.create("https://photos.example.test"));
            var forms = new br.com.fiap.reloop.disposal.service.BootstrapDisposalFormService(photos, disposals, storage, repository);
            var request = new br.com.fiap.reloop.disposal.dto.CreateDisposalFormRequest("COL-001", 1L, "Novo",
                    new org.springframework.mock.web.MockMultipartFile("file", "item.png", "image/png",
                            br.com.fiap.reloop.contract.DisposalFormScenarios.image("png")));
            assertThatThrownBy(() -> forms.create(request)).isInstanceOf(IllegalStateException.class);
            assertThat(repository.findByUserId(42L)).isEmpty();
            assertThat(repository.findByUserId(99L)).containsExactly(oldDisposal);
            assertThat(storage.findById(newId.get())).isEmpty();
            assertThat(storage.read(new UploadedPhoto(newId.get(),42L,"image/png")).exists()).isFalse();
            assertThat(storage.findById(oldPhoto.id())).contains(oldPhoto);
            assertThat(storage.read(oldPhoto).exists()).isTrue();
        } finally { storage.cleanUp(); }
    }

    @Test void rollbackPreservesOtherUploadsAndClearsTheScopeForTheNextRequest() throws Exception {
        var storage = new TemporaryPhotoStorage();
        try {
            var existing = storage.save(1L, "image/png", new ByteArrayInputStream(new byte[]{1,2,3}));
            var reverted = new AtomicReference<UploadedPhoto>();
            assertThatThrownBy(() -> storage.withRollback(() -> {
                try { reverted.set(storage.save(2L, "image/png", new ByteArrayInputStream(new byte[]{4,5,6}))); }
                catch (java.io.IOException failure) { throw new UncheckedIOException(failure); }
                throw new IllegalStateException("disposal rejected");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(storage.findById(existing.id())).contains(existing);
            assertThat(storage.read(existing).getContentAsByteArray()).containsExactly((byte)1,(byte)2,(byte)3);
            assertThat(storage.findById(reverted.get().id())).isEmpty();
            assertThat(storage.read(reverted.get()).exists()).isFalse();
            var committed = storage.withRollback(() -> {
                try { return storage.save(3L, "image/png", new ByteArrayInputStream(new byte[]{7})); }
                catch (java.io.IOException failure) { throw new UncheckedIOException(failure); }
            });
            assertThat(storage.findById(committed.id())).contains(committed);
            assertThat(storage.read(committed).exists()).isTrue();
        } finally { storage.cleanUp(); }
    }
}
