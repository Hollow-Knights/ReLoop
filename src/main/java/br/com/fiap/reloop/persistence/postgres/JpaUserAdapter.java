package br.com.fiap.reloop.persistence.postgres;

import java.util.Optional;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.UserRow;
import br.com.fiap.reloop.user.model.User;
import br.com.fiap.reloop.user.repository.UserRepository;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@Repository
@Profile("postgres")
@Transactional
public class JpaUserAdapter implements UserRepository {
    private final JpaStores.Users users;
    private final WalletRepository wallets;
    private final EntityManager entityManager;
    public JpaUserAdapter(JpaStores.Users users, WalletRepository wallets, EntityManager entityManager) {
        this.users = users;
        this.wallets = wallets;
        this.entityManager = entityManager;
    }
    @Override
    public User create(String name, String email, String passwordHash, String document) {
        // Unique indexes arbitrate concurrent inserts. Expected conflicts must not log CPF in SQL exception details.
        var inserted = entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, document)
                VALUES (:name, :email, :hash, :document)
                ON CONFLICT DO NOTHING RETURNING id
                """)
                .setParameter("name", name).setParameter("email", email)
                .setParameter("hash", passwordHash).setParameter("document", document).getResultList();
        if (inserted.isEmpty()) {
            if (users.findByEmailIgnoreCase(email).isPresent()) throw new EmailAlreadyInUse();
            if (users.existsByDocument(document)) throw new DocumentAlreadyInUse();
            throw new DataIntegrityViolationException("Não foi possível criar o usuário.");
        }
        Long id = ((Number) inserted.getFirst()).longValue();
        wallets.open(id);
        return new User(id, name, email, passwordHash, document);
    }
    @Override @Transactional(readOnly = true)
    public Optional<User> findById(Long id) { return users.findById(id).map(JpaUserAdapter::domain); }
    @Override @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) { return users.findByEmailIgnoreCase(email).map(JpaUserAdapter::domain); }
    @Override
    public Optional<User> updateProfile(Long id, String name, String email) {
        return users.findById(id).map(row -> {
            row.name = name;
            row.email = email;
            row.updatedAt = DatabaseTime.now();
            try { users.saveAndFlush(row); }
            catch (RuntimeException failure) {
                if (SqlConflicts.causedBy(failure, "ux_users_email_lower")) throw new EmailAlreadyInUse();
                throw failure;
            }
            return domain(row);
        });
    }
    private static User domain(UserRow row) { return new User(row.id, row.name, row.email, row.passwordHash, row.document); }
}
