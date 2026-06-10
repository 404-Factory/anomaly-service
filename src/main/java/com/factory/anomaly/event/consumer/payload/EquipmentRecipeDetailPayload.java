package com.factory.anomaly.event.consumer.payload;

import com.factory.common.event.domain.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentRecipeDetailPayload implements EventPayload {
    private Long equipmentRecipeId;
    private String recipeParameter;
    private Double minValue;
    private Double maxValue;
}
// 타 서비스에게 매번 API로 데이터를 요청하면 사용자 경험 측면에서 좋지 않으니까.