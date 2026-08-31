package com.hmg.ipmap.ingestion.file.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hmg.ipmap.common.util.MapperUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;

@Builder
public class JobStatusImportTypeResponseDto {
    @Schema(description = "The file processing details")
    @Builder.Default
    private Map<String, ImportTypeDetailDto> fileTypes = new HashMap<>();

    @JsonAnySetter
    public void setFileTypes(String key, Object value) {
        ImportTypeDetailDto fileType =
                MapperUtil.getObjectMapper().convertValue(value, ImportTypeDetailDto.class);
        fileTypes.put(key, fileType);
    }

    @JsonAnyGetter
    public Map<String, ImportTypeDetailDto> getFileTypes() {
        return fileTypes;
    }
}
