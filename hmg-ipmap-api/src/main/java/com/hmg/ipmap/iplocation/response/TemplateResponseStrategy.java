package com.hmg.ipmap.iplocation.response;

import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;

public interface TemplateResponseStrategy {

    String format(IpLocationDomainData dto);
}
