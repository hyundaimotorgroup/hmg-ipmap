package com.hmg.ipmap.iplocation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IpLocationDomainData {
    @JsonIgnore private boolean ipNotFound;
    private IpMappingDomainData ipMapping;

    private LocationDomainData location;
}
