package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.SitePresentationAssetResponse;
import com.iflytek.skillhub.dto.SitePresentationResponse;
import com.iflytek.skillhub.dto.SitePresentationSettings;
import com.iflytek.skillhub.service.SitePresentationAssetService;
import com.iflytek.skillhub.service.SitePresentationService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SitePresentationControllerTest {
    private static final String SETTINGS = """
            {"settings":{"siteName":"Team Skills","sections":{
              "hero":{"enabled":true,"texts":{"title":{"zh":"团队技能"}},"items":{"preview":false}},
              "popular":{"enabled":false,"texts":{}},"footer":{"enabled":false,"texts":{}}},
              "links":{"repository":"https://code.example.test"}},"version":0}
            """;
    private static final String ADMIN = "/api/v1/admin/system-config/presentation";
    @Autowired private MockMvc mvc;
    @MockBean private SitePresentationService service;
    @MockBean private SitePresentationAssetService assets;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void anonymousCanReadOnlyPublicPresentationWithoutCaching() throws Exception {
        when(service.current()).thenReturn(new SitePresentationResponse(SitePresentationSettings.defaults(), 0));
        mvc.perform(get("/api/v1/site-presentation"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.settings.sections.hero.enabled").value(true))
                .andExpect(jsonPath("$.data.settings.sections.popular.enabled").value(true))
                .andExpect(jsonPath("$.data.settings.sections.footer.enabled").value(true))
                .andExpect(jsonPath("$.data.settings.homepageMode").doesNotExist())
                .andExpect(jsonPath("$.data.settings.heroTitle").doesNotExist())
                .andExpect(jsonPath("$.data.settings.showPopular").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.data.settingKey").doesNotExist());
    }

    @Test
    void anonymousCanReadImmutablePngButNotManagedSettings() throws Exception {
        when(assets.read("asset-id")).thenReturn(new byte[]{1, 2});
        mvc.perform(get("/api/v1/site-presentation/assets/asset-id"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andExpect(content().bytes(new byte[]{1, 2}));
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
    }

    @Test
    void superAdminCanSaveAndUpload() throws Exception {
        when(service.update(any(), eq("admin"), any()))
                .thenReturn(new SitePresentationResponse(SitePresentationSettings.defaults(), 1));
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).with(csrf())
                        .contentType("application/json").content(SETTINGS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        when(assets.upload(any())).thenReturn(new SitePresentationAssetResponse("/api/v1/site-presentation/assets/id"));
        mvc.perform(multipart(ADMIN + "/assets").file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}))
                        .with(authentication(auth("SUPER_ADMIN"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("/api/v1/site-presentation/assets/id"));
    }

    @Test
    void otherAdministratorsCannotReadWriteOrUpload() throws Exception {
        mvc.perform(get(ADMIN).with(authentication(auth("USER_ADMIN")))).andExpect(status().isForbidden());
        mvc.perform(put(ADMIN).with(authentication(auth("USER_ADMIN"))).with(csrf())
                        .contentType("application/json").content(SETTINGS)).andExpect(status().isForbidden());
        mvc.perform(multipart(ADMIN + "/assets").file(new MockMultipartFile("file", new byte[]{1}))
                        .with(authentication(auth("USER_ADMIN"))).with(csrf())).andExpect(status().isForbidden());
        verify(service, never()).update(any(), any(), any());
        verify(assets, never()).upload(any());
    }

    @Test
    void staleVersionReturnsConflictWithoutSuccessEnvelope() throws Exception {
        when(service.update(any(), eq("admin"), any()))
                .thenThrow(new DomainConflictException("error.system.settings.version.conflict"));
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).with(csrf())
                        .contentType("application/json").content(SETTINGS))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void missingCsrfCookieUsesExistingExpiredSessionResponse() throws Exception {
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN")))
                        .contentType("application/json").content(SETTINGS)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.msg").value("Session expired"));
        verify(service, never()).update(any(), any(), any());
    }

    @Test
    void invalidCsrfCannotWriteEvenForSuperAdmin() throws Exception {
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).with(csrf().useInvalidToken())
                        .contentType("application/json").content(SETTINGS)).andExpect(status().isForbidden());
        verify(service, never()).update(any(), any(), any());
    }

    @Test
    void malformedSectionsLocalesLinksAndMissingSettingsDoNotReachService() throws Exception {
        for (String body : List.of(SETTINGS.replace("\"hero\"", "\"custom\""), SETTINGS.replace("\"zh\"", "\"xx\""),
                SETTINGS.replace("\"repository\"", "\"unknown\""), SETTINGS.replace("\"enabled\":true", "\"enabled\":null"),
                "{\"settings\":{},\"version\":0}", "{\"version\":0}",
                "{\"settings\":{\"sections\":{},\"links\":{}},\"version\":0}")) {
            mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).with(csrf())
                            .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verify(service, never()).update(any(), any(), any());
    }

    @Test
    void oldPutBodyIsRejectedRatherThanSilentlyResettingSections() throws Exception {
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).with(csrf())
                        .contentType("application/json").content("""
                            {"settings":{"siteName":"Old","homepageMode":"CATALOG","heroTitle":{},
                              "heroDescription":{},"showPopular":false,"showLatest":false,
                              "showQuickStart":false,"footerVisible":false},"version":0}
                            """))
                .andExpect(status().isBadRequest());
        verify(service, never()).update(any(), any(), any());
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        var principal = new PlatformPrincipal("admin", "admin", "a@example.com", "", "github", Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
