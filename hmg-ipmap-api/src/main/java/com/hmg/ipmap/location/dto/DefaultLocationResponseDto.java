package com.hmg.ipmap.location.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Location response DTO for the {@code default} data-provider. */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DefaultLocationResponseDto extends BaseLocationResponseDto {

    @Schema(description = "The additional region information")
    private LocationDto region;
}
