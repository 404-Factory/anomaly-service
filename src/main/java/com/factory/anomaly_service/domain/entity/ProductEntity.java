package com.factory.anomaly_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "PRODUCT_INFO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductEntity {

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", length = 50, nullable = false)
    private String productName;
}