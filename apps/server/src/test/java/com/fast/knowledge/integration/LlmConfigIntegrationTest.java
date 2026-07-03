package com.fast.knowledge.integration;

import com.fast.knowledge.integration.support.ApiTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LlmConfigIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unauthenticatedCannotAccessLlmConfig() throws Exception {
        mockMvc.perform(get("/system/llm-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void regularUserCannotAccessLlmConfig() throws Exception {
        String adminToken = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);
        String user = ApiTestSupport.uniqueName("llmuser");
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, user);

        String userToken = ApiTestSupport.login(mockMvc, objectMapper, user, "pass1234");
        mockMvc.perform(get("/system/llm-config")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void adminCanReadAndUpdateLlmConfig() throws Exception {
        String token = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);

        mockMvc.perform(get("/system/llm-config")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").exists())
                .andExpect(jsonPath("$.data.apiKeyMask").exists());

        Map<String, Object> body = Map.of(
                "provider", "ollama",
                "baseUrl", "http://localhost:11434/v1",
                "apiKey", "ollama",
                "model", "qwen2.5:7b",
                "allowExternal", true
        );

        mockMvc.perform(put("/system/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("ollama"))
                .andExpect(jsonPath("$.data.configuredInDb").value(true));
    }

    @Test
    void adminTestEndpointValidatesRequest() throws Exception {
        String token = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);

        Map<String, Object> body = Map.of(
                "provider", "deepseek",
                "baseUrl", "https://api.deepseek.com/v1",
                "apiKey", "invalid-key-for-test",
                "model", "deepseek-chat",
                "allowExternal", true
        );

        mockMvc.perform(post("/system/llm-config/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1));
    }
}
