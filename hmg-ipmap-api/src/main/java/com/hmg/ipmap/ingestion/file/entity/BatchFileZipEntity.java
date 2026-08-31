package com.hmg.ipmap.ingestion.file.entity;

import com.hmg.ipmap.common.entity.AuditableEntity;
import com.hmg.ipmap.ingestion.file.enums.ZipStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "batch_file_zip")
public class BatchFileZipEntity extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "batch_id")
    private BatchRunEntity batchRun;

    @Column(name = "name")
    private String name;

    @Column(name = "file_path")
    private String path;

    @Column(name = "zip_type")
    private String zipType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ZipStatusEnum status;

    @Column(name = "error_message")
    private String errorMessage;
}
