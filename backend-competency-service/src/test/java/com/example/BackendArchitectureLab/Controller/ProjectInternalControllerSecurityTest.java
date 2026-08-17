package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Config.InternalApiTokenInterceptor;
import com.example.BackendArchitectureLab.Config.WebMvcConfig;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProjectInternalControllerSecurityTest - 內網端點 X-Internal-Token 真實 HTTP 安全測試（PR #47 複審 P0）。
 * 驗證 /project/inner/skills/restore 在 token 正確／錯誤／缺失三種情境的授權行為。
 */
@WebMvcTest(controllers = ProjectInternalController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
@Import({WebMvcConfig.class, InternalApiTokenInterceptor.class})
@TestPropertySource(properties = "app.internal.token=integration-test-secret")
class ProjectInternalControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ICompensationRestoreService compensationRestoreService;

    /**
     * CompetencyApplication 上的 @EnableJpaAuditing 在 WebMvcTest slice 中會要求
     * jpaMappingContext，而 slice 未掃描 entity（metamodel 為空），故以 mock 取代該 bean。
     */
    @MockBean
    private JpaMetamodelMappingContext jpaMappingContext;

    private static final String API_PATH = "/project/inner/skills/restore";
    private static final String BODY = "[{\"userId\":\"%s\",\"skillId\":\"%s\",\"levelId\":\"%s\"}]";
    private static final String BINDING = "{\"userId\":\"%s\",\"skillId\":\"%s\",\"levelId\":\"%s\"}";

    @Test
    void matchingToken_shouldInvokeRestoreAndReturn200() throws Exception {
        String userId = UUID.randomUUID().toString();
        String skillId = UUID.randomUUID().toString();
        String levelId = UUID.randomUUID().toString();

        mockMvc.perform(post(API_PATH)
                        .param("projectId", UUID.randomUUID().toString())
                        .param("expectedVersion", "1")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Fencing-Owner", "owner-1")
                        .header("X-Fencing-Version", "1")
                        .header("X-Internal-Token", "integration-test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(BODY, userId, skillId, levelId)))
                .andExpect(status().isOk());

        verify(compensationRestoreService).restoreMemberSkills(
                any(UUID.class),
                any(UUID.class),
                eq(1L),
                eq("owner-1"),
                eq(1L),
                anyList());
    }

    @Test
    void wrongToken_shouldReturn401_andNotInvokeRestore() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .param("projectId", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Fencing-Owner", "owner-1")
                        .header("X-Fencing-Version", "1")
                        .header("X-Internal-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized: missing or invalid X-Internal-Token"));

        verifyNoInteractions(compensationRestoreService);
    }

    @Test
    void missingToken_shouldReturn401_andNotInvokeRestore() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .param("projectId", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Fencing-Owner", "owner-1")
                        .header("X-Fencing-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized: missing or invalid X-Internal-Token"));

        verifyNoInteractions(compensationRestoreService);
    }

    @Test
    void tooManyBindings_shouldReturn400_andNotInvokeRestore() throws Exception {
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                body.append(",");
            }
            body.append(String.format(BINDING, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        }
        body.append("]");

        mockMvc.perform(post(API_PATH)
                        .param("projectId", UUID.randomUUID().toString())
                        .param("expectedVersion", "1")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Fencing-Owner", "owner-1")
                        .header("X-Fencing-Version", "1")
                        .header("X-Internal-Token", "integration-test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(compensationRestoreService);
    }

    @Test
    void nonMemberUser_shouldReturn400_whenServiceRejects() throws Exception {
        String userId = UUID.randomUUID().toString();
        String skillId = UUID.randomUUID().toString();
        String levelId = UUID.randomUUID().toString();

        doThrow(new IllegalArgumentException("User " + userId + " is not a member of project"))
                .when(compensationRestoreService)
                .restoreMemberSkills(any(UUID.class), any(UUID.class), eq(1L), eq("owner-1"), eq(1L), anyList());

        mockMvc.perform(post(API_PATH)
                        .param("projectId", UUID.randomUUID().toString())
                        .param("expectedVersion", "1")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Fencing-Owner", "owner-1")
                        .header("X-Fencing-Version", "1")
                        .header("X-Internal-Token", "integration-test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(BODY, userId, skillId, levelId)))
                .andExpect(status().isBadRequest());

        verify(compensationRestoreService).restoreMemberSkills(
                any(UUID.class),
                any(UUID.class),
                eq(1L),
                eq("owner-1"),
                eq(1L),
                anyList());
    }
}