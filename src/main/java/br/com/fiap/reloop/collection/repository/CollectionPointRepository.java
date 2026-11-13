package br.com.fiap.reloop.collection.repository;

import java.util.List;
import java.util.Optional;

import br.com.fiap.reloop.collection.model.CollectionPoint;

public interface CollectionPointRepository {

    List<CollectionPoint> findAllPoints();

    Optional<CollectionPoint> findPointById(Long id);
}
