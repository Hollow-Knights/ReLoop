package br.com.fiap.reloop.collection.dto;

import java.math.BigDecimal;

public record ElectronicCategoryResponse(
        Long id,
        String name,
        int basePoints,
        int pointsPerKg,
        int maxPoints,
        BigDecimal minWeightKg,
        BigDecimal maxWeightKg) {
}
