package com.flycast.emulator.shell;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RetroAchievementsHostUrlValidatorTest {
    @Test
    public void acceptsLoopbackHttpAndHttpsProxyUrls() {
        assertTrue(RetroAchievementsHostUrlValidator.isValid("http://127.0.0.1:8080"));
        assertTrue(RetroAchievementsHostUrlValidator.isValid("https://localhost/api?mode=ra"));
        assertTrue(RetroAchievementsHostUrlValidator.isValid("http://localhost"));
        assertTrue(RetroAchievementsHostUrlValidator.isValid("http://[::1]:8080"));
    }

    @Test
    public void rejectsRemoteAndNonLoopbackDestinations() {
        assertFalse(RetroAchievementsHostUrlValidator.isValid("https://proxy.example.test/api"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://192.168.1.20:8080"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://10.0.2.2:8080"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://127.0.0.2:8080"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://localhost.example.test"));
    }

    @Test
    public void rejectsMissingOrUnsupportedUrlParts() {
        assertFalse(RetroAchievementsHostUrlValidator.isValid(null));
        assertFalse(RetroAchievementsHostUrlValidator.isValid(""));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("ftp://localhost"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("localhost"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http:///missing-host"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://"));
    }

    @Test
    public void rejectsUserInfoAndInvalidPorts() {
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://user:pass@localhost"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://localhost:0"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://localhost:65536"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://localhost:abc"));
    }

    @Test
    public void rejectsControlCharactersWhitespaceAndMalformedUris() {
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://localhost\nHostUrl=evil"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://localhost/path with space"));
        assertFalse(RetroAchievementsHostUrlValidator.isValid("http://[invalid-ipv6"));
    }
}
