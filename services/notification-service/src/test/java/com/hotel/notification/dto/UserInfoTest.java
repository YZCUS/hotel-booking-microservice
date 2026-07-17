package com.hotel.notification.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserInfoTest {

    @Test
    void getFullName_PrefersExplicitFullName() {
        UserInfo user = UserInfo.builder()
                .fullName("Alice Chen")
                .firstName("Ignored")
                .lastName("Name")
                .build();

        assertEquals("Alice Chen", user.getFullName());
    }

    @Test
    void getFullName_FallsBackToFirstAndLastName() {
        UserInfo user = UserInfo.builder()
                .firstName("Alice")
                .lastName("Chen")
                .build();

        assertEquals("Alice Chen", user.getFullName());
    }

    @Test
    void getFullName_FallsBackToGuestWhenNamesAreMissing() {
        UserInfo user = UserInfo.builder().build();

        assertEquals("Guest", user.getFullName());
    }
}
