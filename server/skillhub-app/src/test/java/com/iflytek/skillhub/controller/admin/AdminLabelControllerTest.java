package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.AdminLabelCreateRequest;
import com.iflytek.skillhub.dto.AdminLabelUpdateRequest;
import com.iflytek.skillhub.dto.LabelDefinitionResponse;
import com.iflytek.skillhub.dto.LabelSortOrderItemRequest;
import com.iflytek.skillhub.dto.LabelSortOrderUpdateRequest;
import com.iflytek.skillhub.dto.LabelTranslationItemRequest;
import com.iflytek.skillhub.service.LabelAdminAppService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LabelAdminAppService labelAdminAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void listLabels_returnsDefinitionsForSuperAdmin() throws Exception {
        when(labelAdminAppService.listAll()).thenReturn(List.of(labelResponse("official", "RECOMMENDED", true, 1)));

        mockMvc.perform(get("/api/v1/admin/labels")
                        .with(authentication(superAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("official"))
                .andExpect(jsonPath("$.data[0].type").value("RECOMMENDED"));
    }

    @Test
    void createLabel_returnsCreatedDefinition() throws Exception {
        when(labelAdminAppService.create(any(AdminLabelCreateRequest.class), eq("admin"), any()))
                .thenReturn(labelResponse("official", "RECOMMENDED", true, 1));

        mockMvc.perform(post("/api/v1/admin/labels")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "slug":"official",
                                  "type":"RECOMMENDED",
                                  "visibleInFilter":true,
                                  "sortOrder":1,
                                  "translations":[
                                    {"locale":"en","displayName":"Official"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("official"))
                .andExpect(jsonPath("$.data.translations[0].displayName").value("Official"));

        verify(labelAdminAppService).create(any(AdminLabelCreateRequest.class), eq("admin"), any());
    }

    @Test
    void updateSortOrder_returnsUpdatedDefinitions() throws Exception {
        when(labelAdminAppService.updateSortOrder(any(LabelSortOrderUpdateRequest.class), eq("admin"), any()))
                .thenReturn(List.of(labelResponse("official", "RECOMMENDED", true, 0)));

        mockMvc.perform(put("/api/v1/admin/labels/sort-order")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "items":[
                                    {"slug":"official","sortOrder":0}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0));

        verify(labelAdminAppService).updateSortOrder(any(LabelSortOrderUpdateRequest.class), eq("admin"), any());
    }

    @Test
    void deleteLabel_returnsDeletedEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/labels/official")
                        .with(authentication(superAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("Label deleted"));

        verify(labelAdminAppService).delete(eq("official"), eq("admin"), any());
    }

    @Test
    void mutatingEndpoints_requireSuperAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/labels")
                        .with(authentication(skillAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "slug":"official",
                                  "type":"RECOMMENDED",
                                  "visibleInFilter":true,
                                  "sortOrder":1,
                                  "translations":[
                                    {"locale":"en","displayName":"Official"}
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private LabelDefinitionResponse labelResponse(String slug, String type, boolean visibleInFilter, int sortOrder) {
        return new LabelDefinitionResponse(
                slug,
                type,
                visibleInFilter,
                sortOrder,
                List.of(new com.iflytek.skillhub.dto.LabelTranslationResponse("en", "Official")),
                Instant.parse("2026-03-20T00:00:00Z")
        );
    }

    private UsernamePasswordAuthenticationToken superAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "", "github", Set.of("SUPER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken skillAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal("reviewer", "reviewer", "r@example.com", "", "github", Set.of("SKILL_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));
    }
}
