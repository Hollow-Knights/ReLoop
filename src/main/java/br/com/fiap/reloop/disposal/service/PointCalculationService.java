package br.com.fiap.reloop.disposal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.fiap.reloop.collection.model.ElectronicCategory;
import br.com.fiap.reloop.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PointCalculationService {

    public int calculate(ElectronicCategory category, BigDecimal weightKg) {
        if (weightKg.compareTo(category.minWeightKg()) < 0 || weightKg.compareTo(category.maxWeightKg()) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "WEIGHT_OUT_OF_RANGE",
                    "O peso está fora dos limites da categoria.");
        }
        return weightKg.multiply(BigDecimal.valueOf(category.pointsPerKg()))
                .add(BigDecimal.valueOf(category.basePoints()))
                .setScale(0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(category.maxPoints()))
                .intValueExact();
    }
}
