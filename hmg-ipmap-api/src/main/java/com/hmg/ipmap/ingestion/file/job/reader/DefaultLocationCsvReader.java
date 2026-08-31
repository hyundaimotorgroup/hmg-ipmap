package com.hmg.ipmap.ingestion.file.job.reader;

import com.hmg.ipmap.ingestion.file.job.dto.DefaultLocationDto;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class DefaultLocationCsvReader extends AbstractCsvReader<DefaultLocationDto> {
    @Override
    protected DefaultLocationDto mapRow(String[] row, Long fileDetailId) {
        return new DefaultLocationDto(
                fileDetailId,
                row[0].trim(),
                parseLong(row[1]),
                row[2].trim(),
                row[3].trim(),
                parseLong(row[4]),
                row[5].trim(),
                row[6].trim(),
                parseLong(row[7]),
                row[8].trim(),
                row[9].trim(),
                parseLong(row[10]),
                row[11].trim());
    }

    private static Long parseLong(String value) {
        String trimmed = value.trim();
        return NumberUtils.isParsable(trimmed) ? NumberUtils.createLong(trimmed) : null;
    }

    @Override
    protected boolean validate(String[] row) {
        if (row.length != 12) {
            return false;
        }
        if (StringUtils.isBlank(row[0])) {
            return false;
        }
        // check all geoname id should number
        int[] geonameIdIndices = {1, 4, 7, 10};
        for (int i : geonameIdIndices) {
            if (StringUtils.isNotBlank(row[i]) && !NumberUtils.isParsable(row[i])) {
                return false;
            }
        }
        return true;
    }
}
