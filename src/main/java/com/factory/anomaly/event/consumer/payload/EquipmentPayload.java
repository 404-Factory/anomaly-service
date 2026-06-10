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
public class EquipmentPayload implements EventPayload {
    private Long equipmentId;
    private String name;
    private Long processId;
}
