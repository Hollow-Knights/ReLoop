package br.com.fiap.reloop.persistence.postgres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data stores used only by the postgres adapters. */
public final class JpaStores {
    private JpaStores() { }
    public interface Users extends JpaRepository<UserRow, Long> {
        Optional<UserRow> findByEmailIgnoreCase(String email);
        boolean existsByDocument(String document);
    }
    public interface Points extends JpaRepository<PointRow, Long> { }
    public interface Devices extends JpaRepository<DeviceRow, String> {
        List<DeviceRow> findByCollectionPointIdOrderByDeviceId(Long pointId);
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select d from DeviceRow d where d.deviceId = :id")
        Optional<DeviceRow> lock(@Param("id") String id);
    }
    public interface Categories extends JpaRepository<CategoryRow, Long> { }
    public interface PointCategories extends JpaRepository<PointCategoryRow, PointCategoryId> {
        @Query("select p.id.categoryId from PointCategoryRow p where p.id.pointId = :id order by p.id.categoryId")
        List<Long> categories(@Param("id") Long id);
    }
    public interface Files extends JpaRepository<FileRow, UUID> { }
    public interface Disposals extends JpaRepository<DisposalRow, Long> {
        Optional<DisposalRow> findByIdAndUserId(Long id, Long userId);
        List<DisposalRow> findByUserId(Long userId);
        boolean existsByPhotoId(UUID photoId);
        @Query("select count(d) > 0 from DisposalRow d where d.deviceId = :device and d.status in "
                + "(br.com.fiap.reloop.disposal.model.DisposalStatus.WAITING_WEIGHT, "
                + "br.com.fiap.reloop.disposal.model.DisposalStatus.WAITING_DEPOSIT, "
                + "br.com.fiap.reloop.disposal.model.DisposalStatus.DEPOSIT_IN_PROGRESS)")
        boolean active(@Param("device") String deviceId);
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select d from DisposalRow d where d.id = :id")
        Optional<DisposalRow> lock(@Param("id") Long id);
    }
    public interface Events extends JpaRepository<EventRow, String> { }
    public interface Wallets extends JpaRepository<WalletRow, Long> {
        Optional<WalletRow> findByUserId(Long userId);
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select w from WalletRow w where w.userId = :user")
        Optional<WalletRow> lock(@Param("user") Long userId);
    }
    public interface Transactions extends JpaRepository<TransactionRow, Long> {
        List<TransactionRow> findByWalletIdOrderByCreatedAtDescIdDesc(Long walletId);
        boolean existsByDisposalId(Long id);
        boolean existsByRedemptionId(Long id);
    }
    public interface Rewards extends JpaRepository<RewardRow, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select r from RewardRow r where r.id = :id")
        Optional<RewardRow> lock(@Param("id") Long id);
    }
    public interface Redemptions extends JpaRepository<RedemptionRow, Long> {
        Optional<RedemptionRow> findByUserIdAndIdempotencyKey(Long userId, String key);
        List<RedemptionRow> findByUserId(Long userId);
    }
}
