package com.chrono.chrono.utils;

import com.chrono.chrono.entities.User;

import java.util.Locale;

/** Creates the short, stable display label used for an acting administrator. */
public final class UserInitials {

    private UserInitials() {
    }

    public static String from(User user) {
        if (user == null) {
            return null;
        }

        String firstInitial = firstCharacter(user.getFirstName());
        String lastInitial = firstCharacter(user.getLastName());
        String initials = firstInitial + lastInitial;

        if (initials.isBlank()) {
            String username = user.getUsername() == null ? "" : user.getUsername().trim();
            initials = username.codePoints()
                    .limit(2)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        }

        return initials.isBlank() ? null : initials.toUpperCase(Locale.ROOT);
    }

    private static String firstCharacter(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        int firstCodePoint = trimmed.codePointAt(0);
        return new String(Character.toChars(firstCodePoint));
    }
}
