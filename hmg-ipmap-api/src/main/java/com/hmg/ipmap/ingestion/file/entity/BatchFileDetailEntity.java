package com.hmg.ipmap.ingestion.file.entity;

import com.hmg.ipmap.common.entity.AuditableEntity;
import com.hmg.ipmap.ingestion.file.enums.BatchFileDetailStatusEnum;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "batch_file_detail")
public class BatchFileDetailEntity extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private BatchFileEntity batchFile;

    @Column(name = "line_no")
    private Integer lineNo;

    @Column(name = "error_message")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BatchFileDetailStatusEnum status;

    @Column(name = "line_data", length = 1024)
    private String lineData;

    @Column(name = "line_hash")
    private UUID lineHash;
}
