package com.hmg.ipmap.admin;

import com.hmg.ipmap.admin.dto.OpsRequestDto;
import com.hmg.ipmap.admin.dto.OpsResponseDto;
import com.hmg.ipmap.common.enums.OpsAction;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service layer for administrative operational actions such as cache management.
 *
 * <p>Receives an {@link OpsRequestDto}, determines the appropriate action, and returns a structured
 * {@link OpsResponseDto}.
 */
@Slf4j
@Service
public class OpsServiceImpl implements OpsService {

    private final RedisTemplate<String, String> redisTemplate;

    /** Standard success message returned in every successful {@link OpsResponseDto}. */
    private static final String RESP_SUCC_MSG = "Action executed successfully";

    /**
     * Constructs the service with the provided Redis template.
     *
     * @param redisTemplate the Redis template used for cache operations
     */
    public OpsServiceImpl(@Qualifier("stringRedisTemplate") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OpsResponseDto decideOpsAction(OpsRequestDto opsRequestDto) {

        log.info("Received Ops Request: {}", opsRequestDto);

        if (opsRequestDto == null || opsRequestDto.getAction() == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }

        log.info("Received Ops Action: {}", opsRequestDto.getAction());

        OpsResponseDto response;

        if (Objects.requireNonNull(opsRequestDto.getAction()) == OpsAction.CACHE_FLUSH_ALL) {
            response = flushAllRedisKeys(opsRequestDto);
        } else {
            throw new UnsupportedOperationException("Unknown action: " + opsRequestDto.getAction());
        }

        return response;
    }

    /** Executes a Redis {@code FLUSHALL} command, removing every key from all databases. */
    private OpsResponseDto flushAllRedisKeys(OpsRequestDto opsRequestDto) {

        redisTemplate.execute(
                (RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().flushAll();

                            return null;
                        });

        return buildResponse(opsRequestDto);
    }

    /** Assembles an {@link OpsResponseDto} from the given action, message, and optional detail. */
    private OpsResponseDto buildResponse(OpsRequestDto opsRequestDto) {
        OpsResponseDto response = new OpsResponseDto();
        response.setDetail(null);
        response.setAction(opsRequestDto.getAction());
        response.setMessage(OpsServiceImpl.RESP_SUCC_MSG);
        return response;
    }
}
