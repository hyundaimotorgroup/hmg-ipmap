package com.hmg.ipmap.ingestion.file.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hmg.ipmap.common.util.MapperUtil;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.ObjectMapper;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportTypeDetailDto {

    private String fileName;

    private int progressPercentage;

    private String errorMessage;

    @Builder.Default private Map<String, StepDetailDto> steps = new HashMap<>();

    @JsonAnyGetter
    public Map<String, StepDetailDto> getSteps() {
        return steps;
    }

    @JsonAnySetter
    public void setStep(String key, Object value) {
        ObjectMapper mapper = MapperUtil.getObjectMapper();
        String json = mapper.writeValueAsString(value);
        StepDetailDto step = mapper.readValue(json, StepDetailDto.class);
        steps.put(key, step);
    }
}
