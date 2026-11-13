package br.com.fiap.reloop.collection.model;

import java.math.BigDecimal;

public record ElectronicCategory(
        Long id,
        String name,
        int basePoints,
        int pointsPerKg,
        int maxPoints,
        BigDecimal minWeightKg,
        BigDecimal maxWeightKg) {
}
