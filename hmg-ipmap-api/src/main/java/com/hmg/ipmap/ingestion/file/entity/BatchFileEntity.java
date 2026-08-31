package com.hmg.ipmap.ingestion.file.entity;

import com.hmg.ipmap.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "batch_file")
public class BatchFileEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private BatchRunEntity batchRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_zip_id")
    private BatchFileZipEntity batchFileZip;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "line_count")
    private Integer lineCount;

    @Column(name = "skip_count")
    private Integer skipCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BatchFileStatusEnum status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "path")
    private String path;
}
