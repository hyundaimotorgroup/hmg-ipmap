package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
import com.hmg.ipmap.ingestion.file.job.model.IpBlockAttribute;
import java.util.List;

/** Processes IP block items and attributes with caching, classification, and persistence. */
public interface IpBlockIngestionService {

    void registerIpBlocks(List<IpBlock> ipBlocks);

    void registerIpAttributes(List<IpBlockAttribute> ipBlockAttributes);
}
