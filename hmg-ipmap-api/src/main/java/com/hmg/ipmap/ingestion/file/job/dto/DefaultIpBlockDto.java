package com.hmg.ipmap.ingestion.file.job.dto;

public record DefaultIpBlockDto(
        Long fileDetailId,
        String ipCidr,
        Long geonameId,
        Long registeredCountryGeonameId,
        Long representedCountryGeonameId,
        String longitude,
        String latitude,
        String timezone,
        String postalCode) {}
