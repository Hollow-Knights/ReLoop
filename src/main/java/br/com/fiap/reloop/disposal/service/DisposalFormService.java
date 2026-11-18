package br.com.fiap.reloop.disposal.service;

import br.com.fiap.reloop.disposal.dto.CreateDisposalFormRequest;
import br.com.fiap.reloop.disposal.dto.DisposalResponse;

public interface DisposalFormService {
    DisposalResponse create(CreateDisposalFormRequest request);
}
