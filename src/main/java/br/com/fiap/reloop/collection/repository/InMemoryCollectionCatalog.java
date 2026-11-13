package br.com.fiap.reloop.collection.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import br.com.fiap.reloop.collection.model.CollectionPoint;
import br.com.fiap.reloop.collection.model.CollectionPoint.Device;
import br.com.fiap.reloop.collection.model.ElectronicCategory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("bootstrap")
public class InMemoryCollectionCatalog implements CollectionPointRepository, ElectronicCategoryRepository {

    private final List<CollectionPoint> points = List.of(
            new CollectionPoint(1L, "Ecoponto Centro", "Rua Exemplo, 100", -20.45, -54.62, true,
                    List.of(1L, 2L, 3L, 4L), List.of(new Device("COL-001", true))),
            new CollectionPoint(2L, "Ecoponto Bairro Norte", "Rua Demonstração, 250", -20.42, -54.61, false,
                    List.of(1L, 4L), List.of(new Device("COL-002", false))),
            new CollectionPoint(3L, "Ecoponto Bairro Sul", "Rua Demonstração, 350", -20.49, -54.63, true,
                    List.of(1L, 4L), List.of(new Device("COL-003", true))));

    private final List<ElectronicCategory> categories = List.of(
            new ElectronicCategory(1L, "Celulares", 30, 5, 40,
                    new BigDecimal("0.05"), new BigDecimal("2.0")),
            new ElectronicCategory(2L, "Notebooks", 25, 5, 45,
                    new BigDecimal("0.5"), new BigDecimal("6.0")),
            new ElectronicCategory(3L, "Pequenos eletrodomésticos", 10, 4, 35,
                    new BigDecimal("0.25"), new BigDecimal("15.0")),
            new ElectronicCategory(4L, "Cabos e carregadores", 0, 4, 25,
                    new BigDecimal("0.01"), new BigDecimal("8.0")));

    @Override
    public List<CollectionPoint> findAllPoints() {
        return points;
    }

    @Override
    public Optional<CollectionPoint> findPointById(Long id) {
        return points.stream().filter(point -> point.id().equals(id)).findFirst();
    }

    @Override
    public List<ElectronicCategory> findAllCategories() {
        return categories;
    }
}
