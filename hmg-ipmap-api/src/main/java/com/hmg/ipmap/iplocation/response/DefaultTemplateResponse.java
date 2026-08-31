package com.hmg.ipmap.iplocation.response;

import com.hmg.ipmap.common.util.MapperUtil;
import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import org.springframework.stereotype.Component;

@Component("defaultTemplateResponse")
public class DefaultTemplateResponse implements TemplateResponseStrategy {

    @Override
    public String format(IpLocationDomainData dto) {
        return MapperUtil.getObjectMapper().writeValueAsString(dto);
    }
}
