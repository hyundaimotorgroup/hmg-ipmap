package com.hmg.ipmap.ipmapping.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmg.ipmap.location.dto.DefaultLocationResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** IP mapping response DTO for the {@code default} data-provider. */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DefaultIpMappingResponseDto extends BaseIpMappingResponseDto {

    @Schema(description = "The location hierarchy of the ip mapping")
    private DefaultLocationResponseDto location;
}
