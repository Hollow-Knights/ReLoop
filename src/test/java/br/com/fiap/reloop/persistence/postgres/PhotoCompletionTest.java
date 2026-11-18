package br.com.fiap.reloop.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.FileRow;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(OutputCaptureExtension.class)
class PhotoCompletionTest {
    @TempDir Path root;

    static Stream<Arguments> outcomes() {
        return Stream.of(
                Arguments.of("commit confirmado", TransactionSynchronization.STATUS_COMMITTED, true, false),
                Arguments.of("rollback confirmado", TransactionSynchronization.STATUS_ROLLED_BACK, false, false),
                Arguments.of("resultado desconhecido", TransactionSynchronization.STATUS_UNKNOWN, true, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outcomes")
    void completionOnlyDeletesAfterConfirmedRollback(String label, int status, boolean retained,
            boolean needsReconciliation, CapturedOutput output) throws Exception {
        var rows = mock(JpaStores.Files.class);
        var storage = new JpaPhotoStorage(rows, root);
        byte[] contents = {1, 2, 3};
        TransactionSynchronizationManager.initSynchronization();
        try {
            var uploaded = storage.save(42L, "image/png", new ByteArrayInputStream(contents));
            var saved = ArgumentCaptor.forClass(FileRow.class);
            verify(rows).saveAndFlush(saved.capture());
            var row = saved.getValue();
            Path file = root.resolve(row.storageKey);
            assertThat(row.id).isEqualTo(uploaded.id());
            assertThat(row.userId).isEqualTo(42L);
            assertThat(row.sizeBytes).isEqualTo(contents.length);
            assertThat(Files.readAllBytes(file)).isEqualTo(contents);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(status);
            assertThat(Files.exists(file)).isEqualTo(retained);
            if (retained) assertThat(Files.readAllBytes(file)).isEqualTo(contents);
            // UNKNOWN must not delete metadata or bytes: its actual commit outcome is not known here.
            verifyNoMoreInteractions(rows);
            if (needsReconciliation) {
                assertThat(output.getOut()).contains(uploaded.id().toString());
                assertThat(output.getOut().toLowerCase(Locale.ROOT)).contains("preservado", "reconciliação");
            } else {
                assertThat(output.getOut().toLowerCase(Locale.ROOT)).doesNotContain("reconciliação necessária");
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
