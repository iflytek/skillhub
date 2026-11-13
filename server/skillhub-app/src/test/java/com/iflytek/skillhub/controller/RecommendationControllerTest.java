package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.iflytek.skillhub.dto.RecommendationAdminResponse;
import com.iflytek.skillhub.dto.RecommendationContent;
import com.iflytek.skillhub.dto.RecommendationPublicResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.RecommendationService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class RecommendationControllerTest {
    private static final String ADMIN = "/api/v1/admin/system-config/recommendations";
    @Autowired private MockMvc mvc;
    @MockBean private RecommendationService service;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;
    @Test void publicReadHasNoDraftAndCannotBeCached() throws Exception {
        when(service.published()).thenReturn(new RecommendationPublicResponse(List.of(), List.of()));
        mvc.perform(get("/api/v1/site-recommendations")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.topics").isArray())
            .andExpect(jsonPath("$.data.draft").doesNotExist());
    }
    @Test void ordinaryUserAndAnonymousCannotManage() throws Exception {
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN).with(authentication(auth("USER")))).andExpect(status().isForbidden());
        mvc.perform(post(ADMIN+"/publish").with(authentication(auth("USER"))).with(csrf()).contentType("application/json").content("{\"version\":0}"))
            .andExpect(status().isForbidden());
        verify(service, never()).publish(anyLong(), any(), any());
    }
    @Test void adminWriteRequiresCsrfAndValidContent() throws Exception {
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).contentType("application/json").content("{\"version\":0,\"draft\":{\"topics\":[],\"featuredSkillIds\":[]}}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).with(csrf()).contentType("application/json").content("{\"version\":0,\"draft\":null}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).save(any(), any(), any());
    }
    @Test void adminCanSaveDraftAndPublishWithVersion() throws Exception {
        var response = new RecommendationAdminResponse(2, RecommendationContent.empty(), RecommendationContent.empty(), List.of(), null, false);
        when(service.save(any(), eq("admin"), any())).thenReturn(response);
        when(service.publish(eq(2L), eq("admin"), any())).thenReturn(response);
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).with(csrf()).contentType("application/json").content("{\"version\":1,\"draft\":{\"topics\":[],\"featuredSkillIds\":[]}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(post(ADMIN+"/publish").with(authentication(auth("SUPER_ADMIN"))).with(csrf()).contentType("application/json").content("{\"version\":2}"))
            .andExpect(status().isOk());
        verify(service).publish(eq(2L), eq("admin"), any());
    }
    @Test void unsupportedCoverStyleIsRejectedAtHttpBoundary() throws Exception {
        mvc.perform(put(ADMIN).with(authentication(auth("SUPER_ADMIN"))).with(csrf()).contentType("application/json")
                .content("{\"version\":0,\"draft\":{\"topics\":[{\"id\":\"meeting\",\"title\":\"Meeting\",\"description\":\"\",\"coverStyle\":\"custom-css\",\"enabled\":false,\"skillIds\":[]}],\"featuredSkillIds\":[]}}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).save(any(), any(), any());
    }
    @Test void candidatesForwardTheOptionalLabelWithPaging() throws Exception {
        when(service.candidates("meeting", 1, 20, "productivity")).thenReturn(new PageResponse<>(List.of(), 21, 1, 20));
        mvc.perform(get(ADMIN + "/candidates").with(authentication(auth("SUPER_ADMIN")))
                .param("q", "meeting").param("page", "1").param("label", "productivity"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(21));
        verify(service).candidates("meeting", 1, 20, "productivity");
    }
    private UsernamePasswordAuthenticationToken auth(String role) {
        var principal = new PlatformPrincipal("admin", "admin", "a@example.com", "", "github", Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
}
