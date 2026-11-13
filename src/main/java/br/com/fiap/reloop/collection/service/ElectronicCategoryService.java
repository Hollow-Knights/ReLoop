package br.com.fiap.reloop.collection.service;

import java.util.List;

import br.com.fiap.reloop.collection.dto.ElectronicCategoryResponse;
import br.com.fiap.reloop.collection.repository.ElectronicCategoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class ElectronicCategoryService {

    private final ElectronicCategoryRepository repository;

    public ElectronicCategoryService(ElectronicCategoryRepository repository) {
        this.repository = repository;
    }

    public List<ElectronicCategoryResponse> listCategories() {
        return repository.findAllCategories().stream()
                .map(category -> new ElectronicCategoryResponse(category.id(), category.name(),
                        category.basePoints(), category.pointsPerKg(), category.maxPoints(),
                        category.minWeightKg(), category.maxWeightKg()))
                .toList();
    }
}
