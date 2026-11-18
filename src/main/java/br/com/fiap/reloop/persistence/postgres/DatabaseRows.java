package br.com.fiap.reloop.persistence.postgres;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import br.com.fiap.reloop.disposal.model.DisposalStatus;
import br.com.fiap.reloop.iot.model.IotEventType;
import br.com.fiap.reloop.wallet.model.PointTransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps Hiago's existing SQL. Hibernate validates these rows; it never generates the schema. */
public final class DatabaseRows {
    private DatabaseRows() { }

    @Entity(name = "UserRow")
    @Table(name = "users")
    public static class UserRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false)
        public Long id;
        @Column(name = "name", nullable = false, length = 120)
        public String name;
        @Column(name = "email", nullable = false, length = 254)
        public String email;
        @Column(name = "password_hash", nullable = false, columnDefinition = "text")
        public String passwordHash;
        @Column(name = "created_at", nullable = false)
        public Instant createdAt;
        @Column(name = "updated_at", nullable = false)
        public Instant updatedAt;
    }

    @Entity(name = "PointRow")
    @Table(name = "collection_points")
    public static class PointRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false)
        public Long id;
        @Column(name = "name", nullable = false, columnDefinition = "text")
        public String name;
        @Column(name = "address", nullable = false, columnDefinition = "text")
        public String address;
        @Column(name = "latitude", nullable = false)
        public double latitude;
        @Column(name = "longitude", nullable = false)
        public double longitude;
        @Column(name = "is_active", nullable = false)
        public boolean active;
    }

    @Entity(name = "DeviceRow")
    @Table(name = "collection_devices")
    public static class DeviceRow {
        @Id
        @Column(name = "device_id", nullable = false, length = 64)
        public String deviceId;
        @Column(name = "collection_point_id", nullable = false)
        public Long collectionPointId;
        @Column(name = "is_active", nullable = false)
        public boolean active;
    }

    @Entity(name = "CategoryRow")
    @Table(name = "electronic_categories")
    public static class CategoryRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false)
        public Long id;
        @Column(name = "name", nullable = false, columnDefinition = "text")
        public String name;
        @Column(name = "base_points", nullable = false)
        public int basePoints;
        @Column(name = "points_per_kg", nullable = false)
        public int pointsPerKg;
        @Column(name = "max_points", nullable = false)
        public int maxPoints;
        @Column(name = "min_weight_kg", nullable = false, precision = 10, scale = 3)
        public BigDecimal minWeightKg;
        @Column(name = "max_weight_kg", nullable = false, precision = 10, scale = 3)
        public BigDecimal maxWeightKg;
    }

    @Entity(name = "FileRow")
    @Table(name = "uploaded_files")
    public static class FileRow {
        @Id
        @Column(name = "id", nullable = false)
        public UUID id;
        @Column(name = "user_id", nullable = false)
        public Long userId;
        @Column(name = "storage_key", nullable = false, columnDefinition = "text")
        public String storageKey;
        @Column(name = "content_type", nullable = false, columnDefinition = "text")
        public String contentType;
        @Column(name = "size_bytes", nullable = false)
        public long sizeBytes;
        @Column(name = "created_at", nullable = false)
        public Instant createdAt;
    }

    @Entity(name = "DisposalRow")
    @Table(name = "disposals")
    public static class DisposalRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false)
        public Long id;
        @Column(name = "user_id", nullable = false)
        public Long userId;
        @Column(name = "device_id", nullable = false, length = 64)
        public String deviceId;
        @Column(name = "category_id", nullable = false)
        public Long categoryId;
        @Column(name = "item_name", nullable = false, length = 120)
        public String itemName;
        @Column(name = "photo_id", nullable = false)
        public UUID photoId;
        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 24)
        public DisposalStatus status;
        @Column(name = "weight_kg", nullable = true, precision = 10, scale = 3)
        public BigDecimal weightKg;
        @Column(name = "estimated_points", nullable = true)
        public Integer estimatedPoints;
        @Column(name = "awarded_points", nullable = false)
        public int awardedPoints;
        @Column(name = "created_at", nullable = false)
        public Instant createdAt;
        @Column(name = "updated_at", nullable = false)
        public Instant updatedAt;
        @Column(name = "completed_at", nullable = true)
        public Instant completedAt;
    }

    @Entity(name = "EventRow")
    @Table(name = "iot_events")
    public static class EventRow {
        @Id
        @Column(name = "event_id", nullable = false, length = 128)
        public String eventId;
        @Column(name = "device_id", nullable = false, length = 64)
        public String deviceId;
        @Column(name = "disposal_id", nullable = false)
        public Long disposalId;
        @Enumerated(EnumType.STRING)
        @Column(name = "event_type", nullable = false, length = 24)
        public IotEventType eventType;
        @Column(name = "weight_kg", nullable = true, precision = 10, scale = 3)
        public BigDecimal weightKg;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "response_payload", nullable = false, columnDefinition = "jsonb")
        public JsonNode responsePayload;
        @Column(name = "processed_at", nullable = false)
        public Instant processedAt;
    }

    @Entity(name = "WalletRow")
    @Table(name = "wallets")
    public static class WalletRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false)
        public Long id;
        @Column(name = "user_id", nullable = false)
        public Long userId;
        @Column(name = "available_points", nullable = false)
        public int availablePoints;
        @Column(name = "total_earned", nullable = false)
        public int totalEarned;
        @Column(name = "total_redeemed", nullable = false)
        public int totalRedeemed;
    }

    @Entity(name = "RewardRow")
    @Table(name = "rewards")
    public static class RewardRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false)
        public Long id;
        @Column(name = "name", nullable = false, columnDefinition = "text")
        public String name;
        @Column(name = "description", nullable = false, columnDefinition = "text")
        public String description;
        @Column(name = "partner_name", nullable = false, columnDefinition = "text")
        public String partnerName;
        @Column(name = "cost_points", nullable = false)
        public int costPoints;
        @Column(name = "available_quantity", nullable = false)
        public int availableQuantity;
        @Column(name = "active", nullable = false)
        public boolean active;
    }

    @Entity(name = "RedemptionRow")
    @Table(name = "redemptions")
    public static class RedemptionRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false)
        public Long id;
        @Column(name = "user_id", nullable = false)
        public Long userId;
        @Column(name = "reward_id", nullable = false)
        public Long rewardId;
        @Column(name = "reward_name", nullable = false, columnDefinition = "text")
        public String rewardName;
        @Column(name = "points_spent", nullable = false)
        public int pointsSpent;
        @Column(name = "remaining_points", nullable = false)
        public int remainingPoints;
        @Column(name = "coupon_code", nullable = false, columnDefinition = "text")
        public String couponCode;
        @Column(name = "idempotency_key", nullable = false, length = 128)
        public String idempotencyKey;
        @Column(name = "redeemed_at", nullable = false)
        public Instant redeemedAt;
    }

    @Entity(name = "TransactionRow")
    @Table(name = "point_transactions")
    public static class TransactionRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false)
        public Long id;
        @Column(name = "wallet_id", nullable = false)
        public Long walletId;
        @Enumerated(EnumType.STRING)
        @Column(name = "type", nullable = false, length = 6)
        public PointTransactionType type;
        @Column(name = "points", nullable = false)
        public int points;
        @Column(name = "description", nullable = false, columnDefinition = "text")
        public String description;
        @Column(name = "disposal_id", nullable = true)
        public Long disposalId;
        @Column(name = "redemption_id", nullable = true)
        public Long redemptionId;
        @Column(name = "created_at", nullable = false)
        public Instant createdAt;
    }

    @Embeddable
    public static class PointCategoryId implements Serializable {
        @Column(name = "collection_point_id") public Long pointId;
        @Column(name = "category_id") public Long categoryId;
        public PointCategoryId() { }
        @Override public boolean equals(Object other) {
            return other instanceof PointCategoryId key && Objects.equals(pointId, key.pointId)
                    && Objects.equals(categoryId, key.categoryId);
        }
        @Override public int hashCode() { return Objects.hash(pointId, categoryId); }
    }

    @Entity(name = "PointCategoryRow")
    @Table(name = "collection_point_categories")
    public static class PointCategoryRow {
        @EmbeddedId public PointCategoryId id;
    }
}
