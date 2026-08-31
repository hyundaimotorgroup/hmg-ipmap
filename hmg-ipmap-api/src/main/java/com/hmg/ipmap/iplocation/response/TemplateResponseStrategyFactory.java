package com.hmg.ipmap.iplocation.response;

import com.hmg.ipmap.user.UserResponseTemplateEnum;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TemplateResponseStrategyFactory {

    static final String DEFAULT_STRATEGY = "defaultTemplateResponse";

    private final Map<String, TemplateResponseStrategy> strategies;

    public TemplateResponseStrategyFactory(Map<String, TemplateResponseStrategy> strategies) {
        this.strategies = strategies;
    }

    public TemplateResponseStrategy get(UserResponseTemplateEnum template) {
        if (template == null) {
            return strategies.get(DEFAULT_STRATEGY);
        }
        return strategies.getOrDefault(template.getBeanName(), strategies.get(DEFAULT_STRATEGY));
    }
}
