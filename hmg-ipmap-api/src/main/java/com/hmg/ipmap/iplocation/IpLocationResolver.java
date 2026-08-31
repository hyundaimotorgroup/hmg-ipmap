package com.hmg.ipmap.iplocation;

import com.hmg.ipmap.iplocation.dto.IpLocationDomainData;
import java.util.Optional;

public interface IpLocationResolver {
    Optional<IpLocationDomainData> resolve(String ip);
}
