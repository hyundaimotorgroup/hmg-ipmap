package com.hmg.ipmap.cache.dto;

import com.hmg.ipmap.common.enums.UserType;
import com.univocity.parsers.annotations.Parsed;
import lombok.Data;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserCacheDto {

    @Parsed(index = 0)
    private long id;

    @Parsed(index = 1)
    private String apiKey;

    @Parsed(index = 2)
    private String name;

    @Parsed(index = 3)
    private String sourceIp;

    @Parsed(index = 4)
    private UserType userType;

    @Parsed(index = 5)
    private long parentId;
}
