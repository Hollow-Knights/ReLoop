package br.com.fiap.reloop.collection.repository;

import java.util.List;

import br.com.fiap.reloop.collection.model.ElectronicCategory;

public interface ElectronicCategoryRepository {

    List<ElectronicCategory> findAllCategories();
}
