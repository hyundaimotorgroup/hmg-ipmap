package com.hmg.ipmap.ingestion.file.job.dto;

public record DefaultLocationDto(
        Long fileDetailId,
        String continentCode,
        Long continentGeonameId,
        String continentName,
        String countryCode,
        Long countryGeonameId,
        String countryName,
        String subdivisionCode,
        Long subdivisionGeonameId,
        String subdivisionName,
        String cityCode,
        Long cityGeonameId,
        String cityName) {}
