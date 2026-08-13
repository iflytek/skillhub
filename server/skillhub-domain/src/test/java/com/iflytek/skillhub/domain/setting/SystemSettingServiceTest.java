package com.iflytek.skillhub.domain.setting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    private static final String KEY = "demo.group";
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DemoSettings(boolean enabled, String template) {
    }

    @Mock
    private SystemSettingRepository systemSettingRepository;

    private SystemSettingService service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingService(
                systemSettingRepository,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void getReturnsDefaultsWhenGroupWasNeverOverridden() {
        DemoSettings defaults = new DemoSettings(false, "${username}");
        when(systemSettingRepository.findBySettingKey(KEY)).thenReturn(Optional.empty());

        assertSame(defaults, service.get(KEY, DemoSettings.class, defaults));
    }

    @Test
    void getReturnsStoredGroupWhenPresent() {
        when(systemSettingRepository.findBySettingKey(KEY)).thenReturn(Optional.of(
                new SystemSetting(KEY, "{\"enabled\":true,\"template\":\"${username}-space\"}", "usr_1", NOW)));

        DemoSettings resolved = service.get(KEY, DemoSettings.class, new DemoSettings(false, "${username}"));

        assertEquals(new DemoSettings(true, "${username}-space"), resolved);
    }

    @Test
    void getIgnoresUnknownFieldsSoOlderNodesCanReadNewerDocuments() {
        when(systemSettingRepository.findBySettingKey(KEY)).thenReturn(Optional.of(
                new SystemSetting(KEY, "{\"enabled\":true,\"template\":\"x\",\"addedLater\":42}", "usr_1", NOW)));

        assertEquals(new DemoSettings(true, "x"),
                service.get(KEY, DemoSettings.class, new DemoSettings(false, "${username}")));
    }

    @Test
    void getFallsBackToDefaultsWhenStoredDocumentIsMalformed() {
        DemoSettings defaults = new DemoSettings(false, "${username}");
        when(systemSettingRepository.findBySettingKey(KEY)).thenReturn(Optional.of(
                new SystemSetting(KEY, "not json", "usr_1", NOW)));

        assertSame(defaults, service.get(KEY, DemoSettings.class, defaults));
    }

    @Test
    void putStoresSerializedGroupWithActorAndTimestamp() {
        when(systemSettingRepository.findBySettingKey(KEY)).thenReturn(Optional.empty());

        service.put(KEY, new DemoSettings(true, "${username}"), "usr_admin");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository).save(captor.capture());
        SystemSetting saved = captor.getValue();
        assertEquals(KEY, saved.getSettingKey());
        assertEquals("{\"enabled\":true,\"template\":\"${username}\"}", saved.getSettingValue());
        assertEquals("usr_admin", saved.getUpdatedBy());
        assertEquals(NOW, saved.getUpdatedAt());
    }

    @Test
    void putOverwritesExistingRowInPlace() {
        SystemSetting existing = new SystemSetting(KEY, "{\"enabled\":false,\"template\":\"old\"}",
                "usr_previous", NOW.minusSeconds(60));
        when(systemSettingRepository.findBySettingKey(KEY)).thenReturn(Optional.of(existing));

        service.put(KEY, new DemoSettings(true, "new"), "usr_admin");

        verify(systemSettingRepository).save(any(SystemSetting.class));
        assertEquals("{\"enabled\":true,\"template\":\"new\"}", existing.getSettingValue());
        assertEquals("usr_admin", existing.getUpdatedBy());
        assertEquals(NOW, existing.getUpdatedAt());
    }
}
