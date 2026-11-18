package br.com.fiap.reloop.persistence.postgres;

import java.util.Optional;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.UserRow;
import br.com.fiap.reloop.user.model.User;
import br.com.fiap.reloop.user.repository.UserRepository;
import br.com.fiap.reloop.wallet.repository.WalletRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional
public class JpaUserAdapter implements UserRepository {
    private final JpaStores.Users users;
    private final WalletRepository wallets;
    public JpaUserAdapter(JpaStores.Users users, WalletRepository wallets) {
        this.users = users;
        this.wallets = wallets;
    }
    @Override
    public User create(String name, String email, String passwordHash) {
        var row = new UserRow();
        row.name = name;
        row.email = email;
        row.passwordHash = passwordHash;
        row.createdAt = row.updatedAt = DatabaseTime.now();
        try {
            users.saveAndFlush(row);
            wallets.open(row.id);
            return domain(row);
        } catch (RuntimeException failure) {
            if (SqlConflicts.causedBy(failure, "ux_users_email_lower")) throw new EmailAlreadyInUse();
            throw failure;
        }
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
    private static User domain(UserRow row) { return new User(row.id, row.name, row.email, row.passwordHash); }
}
