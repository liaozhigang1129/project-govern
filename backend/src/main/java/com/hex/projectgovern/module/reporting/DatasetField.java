package com.hex.projectgovern.module.reporting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dataset_field")
@Getter @Setter @NoArgsConstructor
public class DatasetField {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "dataset_id", nullable = false) private Long datasetId;
    @Column(name = "field_name", nullable = false, length = 64) private String fieldName;
    @Column(name = "display_name", nullable = false, length = 128) private String displayName;
    @Column(name = "field_type", nullable = false, length = 16) private String fieldType;
    @Column(name = "data_type", nullable = false, length = 16) private String dataType;
    @Column(name = "agg_func", length = 16) private String aggFunc;
    @Column(columnDefinition = "TEXT") private String formula;
    @Column(name = "dim_role", length = 16) private String dimRole;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
}
