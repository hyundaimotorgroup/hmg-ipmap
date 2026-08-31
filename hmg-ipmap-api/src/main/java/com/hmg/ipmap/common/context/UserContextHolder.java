package com.hmg.ipmap.common.context;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> TL = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void set(UserContext ctx) {
        TL.set(ctx);
    }

    public static UserContext get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }
}
