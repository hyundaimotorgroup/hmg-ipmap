package com.hmg.ipmap.ingestion.file.job.writer;

import com.hmg.ipmap.ingestion.file.job.IpBlockIngestionService;
import com.hmg.ipmap.ingestion.file.job.dto.DefaultIpBlockDto;
import com.hmg.ipmap.ingestion.file.job.mapper.DefaultIpBlockMapper;
import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
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
public class DefaultIpBlockWriter implements ItemWriter<DefaultIpBlockDto> {

    private final IpBlockIngestionService ipBlockIngestionService;
    private final DefaultIpBlockMapper defaultIpBlockMapper;

    @Override
    public void write(Chunk<? extends DefaultIpBlockDto> chunk) throws Exception {
        List<IpBlock> ipBlocks = new ArrayList<>();
        for (DefaultIpBlockDto defaultIpBlockDto : chunk) {
            ipBlocks.add(defaultIpBlockMapper.toIpBlock(defaultIpBlockDto));
        }
        ipBlockIngestionService.registerIpBlocks(ipBlocks);
    }
}
