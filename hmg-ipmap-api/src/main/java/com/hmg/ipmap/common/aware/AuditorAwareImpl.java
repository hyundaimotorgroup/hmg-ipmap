package com.hmg.ipmap.common.aware;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component
public class AuditorAwareImpl implements AuditorAware<Long> {
    @Override
    public Optional<Long> getCurrentAuditor() {
        UserContext userContext = UserContextHolder.get();
        if (userContext == null) {
            return Optional.empty();
        }
        return Optional.of(userContext.id());
    }
}
