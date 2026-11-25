package com.chrono.chrono.utils;

import com.chrono.chrono.entities.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserInitialsTest {

    @Test
    void createsInitialsFromFirstAndLastName() {
        User admin = new User();
        admin.setFirstName("Anna");
        admin.setLastName("Berger");
        admin.setUsername("aberger");

        assertEquals("AB", UserInitials.from(admin));
    }

    @Test
    void fallsBackToFirstTwoUsernameCharacters() {
        User admin = new User();
        admin.setUsername("itSupport");

        assertEquals("IT", UserInitials.from(admin));
    }
}
