package br.com.fiap.reloop.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import br.com.fiap.reloop.auth.dto.RegisterUserRequest;
import br.com.fiap.reloop.support.TestDocuments;
import br.com.fiap.reloop.user.model.User;
import br.com.fiap.reloop.user.repository.InMemoryUserRepository;
import br.com.fiap.reloop.user.repository.UserRepository;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;

class UserDocumentPrivacyTest {
    @Test void diagnosticStringsDoNotRevealDocumentOrPassword() {
        String document = TestDocuments.next();
        assertThat(new RegisterUserRequest("Teste", "test@example.test", "secret-password", document).toString())
                .doesNotContain(document, "secret-password");
        assertThat(new User(1L, "Teste", "test@example.test", "secret-hash", document).toString())
                .doesNotContain(document, "secret-hash");
    }

    @Test void failedBootstrapWalletCreationDoesNotConsumeIdentityOrDocument() {
        var wallets = mock(WalletRepository.class);
        var users = new InMemoryUserRepository(wallets);
        String document = TestDocuments.next();
        doThrow(new IllegalStateException("wallet unavailable")).doNothing().when(wallets).open(1L);
        assertThatThrownBy(() -> users.create("Teste", "test@example.test", "hash", document)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmail("test@example.test")).isEmpty();
        assertThat(users.create("Teste", "test@example.test", "hash", document).id()).isEqualTo(1L);
        assertThatThrownBy(() -> users.create("Other", "other@example.test", "hash", document))
                .isInstanceOf(UserRepository.DocumentAlreadyInUse.class);
        verify(wallets, times(2)).open(1L);
        verifyNoMoreInteractions(wallets);
    }
}
