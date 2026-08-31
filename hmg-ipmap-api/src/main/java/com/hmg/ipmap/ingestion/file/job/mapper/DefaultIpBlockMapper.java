package com.hmg.ipmap.ingestion.file.job.mapper;

import com.hmg.ipmap.ingestion.file.job.dto.DefaultIpBlockDto;
import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
import com.hmg.ipmap.ingestion.file.job.model.IpBlockAttribute;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DefaultIpBlockMapper {

    @Mapping(target = "network", source = "ipCidr")
    @Mapping(target = "attribute", source = ".")
    IpBlock toIpBlock(DefaultIpBlockDto defaultIpBlockDto);

    @Mapping(target = "network", source = "ipCidr")
    IpBlockAttribute toIpBlockAttribute(DefaultIpBlockDto defaultIpBlockDto);
}
