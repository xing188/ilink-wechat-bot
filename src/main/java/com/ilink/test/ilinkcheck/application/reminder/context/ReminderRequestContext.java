package com.ilink.test.ilinkcheck.application.reminder.context;

import java.util.Optional;

public final class ReminderRequestContext {

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private ReminderRequestContext() {
    }

    public static void setCurrentUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            CURRENT_USER_ID.remove();
            return;
        }
        CURRENT_USER_ID.set(userId);
    }

    public static Optional<String> currentUserId() {
        return Optional.ofNullable(CURRENT_USER_ID.get());
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
