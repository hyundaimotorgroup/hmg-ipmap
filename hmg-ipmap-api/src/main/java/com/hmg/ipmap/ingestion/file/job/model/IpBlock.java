package com.hmg.ipmap.ingestion.file.job.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IpBlock {
    private Long fileDetailId;
    private String network;
    private Long geonameId;
    private Long registeredCountryGeonameId;
    private Long representedCountryGeonameId;

    private IpBlockAttribute attribute;
}
