package br.com.fiap.reloop.persistence.postgres;

import java.util.List;
import java.util.Optional;
import br.com.fiap.reloop.collection.model.CollectionPoint;
import br.com.fiap.reloop.collection.model.ElectronicCategory;
import br.com.fiap.reloop.collection.repository.CollectionPointRepository;
import br.com.fiap.reloop.collection.repository.ElectronicCategoryRepository;
import br.com.fiap.reloop.persistence.postgres.DatabaseRows.PointRow;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class JpaCatalogAdapter implements CollectionPointRepository, ElectronicCategoryRepository {
    private final JpaStores.Points points;
    private final JpaStores.Devices devices;
    private final JpaStores.PointCategories accepted;
    private final JpaStores.Categories categories;
    public JpaCatalogAdapter(JpaStores.Points points, JpaStores.Devices devices,
            JpaStores.PointCategories accepted, JpaStores.Categories categories) {
        this.points = points; this.devices = devices; this.accepted = accepted; this.categories = categories;
    }
    @Override public List<CollectionPoint> findAllPoints() {
        return points.findAll(Sort.by("id")).stream().map(this::domain).toList();
    }
    @Override public Optional<CollectionPoint> findPointById(Long id) { return points.findById(id).map(this::domain); }
    @Override public List<ElectronicCategory> findAllCategories() {
        return categories.findAll(Sort.by("id")).stream().map(row -> new ElectronicCategory(row.id, row.name,
                row.basePoints, row.pointsPerKg, row.maxPoints, row.minWeightKg, row.maxWeightKg)).toList();
    }
    private CollectionPoint domain(PointRow row) {
        return new CollectionPoint(row.id, row.name, row.address, row.latitude, row.longitude, row.active,
                accepted.categories(row.id), devices.findByCollectionPointIdOrderByDeviceId(row.id).stream()
                        .map(device -> new CollectionPoint.Device(device.deviceId, device.active)).toList());
    }
}
