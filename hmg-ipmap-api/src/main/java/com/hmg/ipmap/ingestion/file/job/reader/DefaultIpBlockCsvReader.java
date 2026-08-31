package com.hmg.ipmap.ingestion.file.job.reader;

import com.hmg.ipmap.ingestion.file.job.dto.DefaultIpBlockDto;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class DefaultIpBlockCsvReader extends AbstractCsvReader<DefaultIpBlockDto> {
    @Override
    protected DefaultIpBlockDto mapRow(String[] row, Long fileDetailId) {
        return new DefaultIpBlockDto(
                fileDetailId,
                row[0].trim(),
                parseLong(row[1]),
                parseLong(row[2]),
                parseLong(row[3]),
                row[4].trim(),
                row[5].trim(),
                row[6].trim(),
                row[7].trim());
    }

    private static Long parseLong(String value) {
        String trimmed = value.trim();
        return NumberUtils.isParsable(trimmed) ? NumberUtils.createLong(trimmed) : null;
    }

    @Override
    protected boolean validate(String[] row) {
        if (row.length != 8) {
            return false;
        }
        if (StringUtils.isBlank(row[0]) && StringUtils.isBlank(row[1])) {
            return false;
        }
        int[] geonameIdIndices = {1, 2, 3};
        for (int i : geonameIdIndices) {
            if (StringUtils.isNotBlank(row[i]) && !NumberUtils.isParsable(row[i].trim())) {
                return false;
            }
        }
        return true;
    }
}
