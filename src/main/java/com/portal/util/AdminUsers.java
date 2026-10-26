package com.portal.util;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdminUsers {

    private AdminUsers() {
    }

    public static boolean isAdmin(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return configured().contains(userId.trim());
    }

    private static Set<String> configured() {
        return Arrays.stream(AppConfig.get("admin.users", "").split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
