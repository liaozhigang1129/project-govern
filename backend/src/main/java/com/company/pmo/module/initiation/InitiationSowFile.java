package com.company.pmo.module.initiation;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 立项 SOW 文件元数据(实体存 /uploads/sow/{initiationId}/{filename},本表只存元信息)。
 * <p>对齐 V3.0 {@code initiation_sow_file} 表。
 * <p>支持一对多(同一立项可上传多版本 SOW),但 UI 上传时默认覆盖。
 */
@Entity
@Table(name = "initiation_sow_file")
@Getter @Setter @NoArgsConstructor
public class InitiationSowFile extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "initiation_id", nullable = false)
    private Long initiationId;

    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();
}
