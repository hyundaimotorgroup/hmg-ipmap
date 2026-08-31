package com.hmg.ipmap.iplocation;

import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import com.hmg.ipmap.ipmapping.dto.IpMappingLocationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IpLocationMapper {

    @Mapping(target = "location.continent", source = "continent")
    @Mapping(target = "location.country", source = "country")
    @Mapping(target = "location.additionalLocations", source = "additionalLocations")
    @Mapping(target = "location.city", source = "city")
    @Mapping(target = "ipMapping", ignore = true)
    IpLocationDomainData ipMappingDtoToIpLocationDto(IpMappingLocationDto ipMappingLocationDto);
}
