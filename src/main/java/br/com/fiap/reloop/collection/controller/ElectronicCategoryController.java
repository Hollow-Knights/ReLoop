package br.com.fiap.reloop.collection.controller;

import java.util.List;

import br.com.fiap.reloop.collection.dto.ElectronicCategoryResponse;
import br.com.fiap.reloop.collection.service.ElectronicCategoryService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("bootstrap")
public class ElectronicCategoryController {

    private final ElectronicCategoryService service;

    public ElectronicCategoryController(ElectronicCategoryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/electronic-categories")
    public List<ElectronicCategoryResponse> listCategories() {
        return service.listCategories();
    }
}
