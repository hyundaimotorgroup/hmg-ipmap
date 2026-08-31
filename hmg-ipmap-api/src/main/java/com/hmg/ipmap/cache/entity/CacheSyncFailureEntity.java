package com.hmg.ipmap.cache.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Table(name = "cache_sync_failure")
@Getter
@Setter
@Entity
public class CacheSyncFailureEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 25, nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String data;

    @Column(length = 2)
    private String region;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(columnDefinition = "TIMESTAMP")
    private Instant attemptedAt;
}
