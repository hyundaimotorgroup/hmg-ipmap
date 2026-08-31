package com.hmg.ipmap.ipmapping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hmg.ipmap.location.dto.DefaultLocationRequestDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** IP mapping request DTO for the {@code default} data-provider. */
@Getter
@Setter
@NoArgsConstructor
public class DefaultIpMappingRequestDto extends BaseIpMappingRequestDto {

    @Schema(description = "The location hierarchy of the ip mapping")
    @JsonProperty("location")
    private @Valid @NotNull DefaultLocationRequestDto location;
}
