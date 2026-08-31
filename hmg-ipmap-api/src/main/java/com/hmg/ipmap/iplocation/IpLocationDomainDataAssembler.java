package com.hmg.ipmap.iplocation;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import com.hmg.ipmap.iplocation.dto.IpMappingDomainData;
import com.hmg.ipmap.ipmapping.dto.IpMappingResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IpLocationDomainDataAssembler {

    private final IpLocationMapper ipLocationMapper;

    public IpLocationDomainData assemble(IpMappingResponseDto dto, String ip, Scope scope) {
        IpLocationDomainData domainData =
                ipLocationMapper.ipMappingDtoToIpLocationDto(dto.getLocation());
        if (domainData == null) {
            return null;
        }
        domainData.setIpMapping(
                new IpMappingDomainData(
                        ip, scope, null, dto.getRepresentedCountry(), dto.getRegisteredCountry()));

        if (dto.getAttributes() != null && !dto.getAttributes().isEmpty()) {
            domainData.getIpMapping().setAttributes(dto.getAttributes());
        }

        return domainData;
    }
}
