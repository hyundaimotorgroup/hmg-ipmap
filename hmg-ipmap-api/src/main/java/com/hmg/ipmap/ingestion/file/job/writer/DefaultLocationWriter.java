package com.hmg.ipmap.ingestion.file.job.writer;

import com.hmg.ipmap.ingestion.file.job.LocationIngestionService;
import com.hmg.ipmap.ingestion.file.job.dto.DefaultLocationDto;
import com.hmg.ipmap.ingestion.file.job.mapper.DefaultLocationMapper;
import com.hmg.ipmap.ingestion.file.job.model.DefaultLocation;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(value = "app.data-provider", havingValue = "default")
public class DefaultLocationWriter implements ItemWriter<DefaultLocationDto> {

    private final LocationIngestionService<DefaultLocation> locationIngestionService;
    private final DefaultLocationMapper defaultLocationMapper;

    @Override
    public void write(Chunk<? extends DefaultLocationDto> chunk) throws Exception {
        List<DefaultLocation> locations = new ArrayList<>();
        for (DefaultLocationDto location : chunk) {
            locations.add(defaultLocationMapper.toLocation(location));
        }
        locationIngestionService.registerLocation(locations);
    }
}
