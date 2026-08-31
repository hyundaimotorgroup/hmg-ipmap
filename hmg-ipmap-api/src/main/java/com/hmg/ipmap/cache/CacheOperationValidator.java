package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.CacheOperationError;
import com.hmg.ipmap.cache.dto.CacheOperationRequestDto;
import com.hmg.ipmap.cache.enums.CacheOpsAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CacheOperationValidator {
    public List<CacheOperationError> validate(CacheOperationRequestDto opsReqDto) {
        List<CacheOperationError> errors = new ArrayList<>();
        opsReqDto
                .getOperations()
                .forEach(
                        cacheOperation ->
                                check(cacheOperation, cacheOperation.getAction())
                                        .ifPresent(errors::add));
        return errors;
    }

    private Optional<CacheOperationError> check(
            CacheOperation cacheOperation, CacheOpsAction action) {
        List<String> columns = action.cacheTable.registeredColumns();

        // Check if any extra columns exist that are not registered
        for (String key : cacheOperation.getData().keySet()) {
            if (!columns.contains(key)) {
                return Optional.of(
                        CacheOperationError.builder()
                                .action(action)
                                .data(cacheOperation.getData())
                                .errorMessage(String.format("Unknown column %s", key))
                                .build());
            }
        }
        return Optional.empty();
    }
}
