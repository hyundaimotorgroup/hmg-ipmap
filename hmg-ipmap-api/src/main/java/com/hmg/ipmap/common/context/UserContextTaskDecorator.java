package com.hmg.ipmap.common.context;

import org.springframework.core.task.TaskDecorator;

public class UserContextTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        UserContext context = UserContextHolder.get();
        return () -> {
            try {
                if (context != null) {
                    UserContextHolder.set(context);
                }
                runnable.run();
            } finally {
                UserContextHolder.clear();
            }
        };
    }
}
