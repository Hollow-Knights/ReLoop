package br.com.fiap.reloop.disposal.controller;

import java.util.List;

import br.com.fiap.reloop.disposal.dto.CreateDisposalRequest;
import br.com.fiap.reloop.disposal.dto.DisposalHistoryResponse;
import br.com.fiap.reloop.disposal.dto.DisposalResponse;
import br.com.fiap.reloop.disposal.service.DisposalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Profile("bootstrap")
public class DisposalController {

    private final DisposalService service;

    public DisposalController(DisposalService service) {
        this.service = service;
    }

    @PostMapping(path = "/disposals", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DisposalResponse create(@Valid @RequestBody CreateDisposalRequest request) {
        return service.create(request);
    }

    @GetMapping("/disposals/{id}")
    public DisposalResponse find(@PathVariable @Min(1) Long id) {
        return service.find(id);
    }

    @DeleteMapping("/disposals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable @Min(1) Long id) {
        service.cancel(id);
    }

    @GetMapping("/users/me/disposals")
    public List<DisposalHistoryResponse> history() {
        return service.history();
    }
}
