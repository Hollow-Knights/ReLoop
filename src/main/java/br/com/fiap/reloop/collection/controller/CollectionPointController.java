package br.com.fiap.reloop.collection.controller;

import java.util.List;

import br.com.fiap.reloop.collection.dto.CollectionPointDetailResponse;
import br.com.fiap.reloop.collection.dto.CollectionPointResponse;
import br.com.fiap.reloop.collection.service.CollectionPointService;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/collection-points")
@Profile("bootstrap")
public class CollectionPointController {

    private final CollectionPointService service;

    public CollectionPointController(CollectionPointService service) {
        this.service = service;
    }

    @GetMapping
    public List<CollectionPointResponse> listPoints() {
        return service.listPoints();
    }

    @GetMapping("/{id}")
    public CollectionPointDetailResponse getPoint(@PathVariable @Min(1) Long id) {
        return service.findPoint(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
