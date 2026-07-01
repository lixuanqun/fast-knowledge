package com.fast.knowledge.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public final class ApiTestSupport {

    private ApiTestSupport() {
    }

    public static String login(MockMvc mockMvc, ObjectMapper objectMapper,
                               String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(0, root.path("code").asInt(), root.path("message").asText());
        return root.path("data").path("token").asText();
    }

    public static String loginAsAdmin(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return login(mockMvc, objectMapper, "admin", "admin123");
    }

    public static String createUser(MockMvc mockMvc, ObjectMapper objectMapper,
                                    String adminToken, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pass1234\","
                                + "\"displayName\":\"" + username + "\",\"role\":\"USER\"}"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(0, root.path("code").asInt(), root.path("message").asText());
        return username;
    }

    public static long createKb(MockMvc mockMvc, ObjectMapper objectMapper,
                                String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/kbs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"test\"}"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(0, root.path("code").asInt(), root.path("message").asText());
        return root.path("data").path("id").asLong();
    }

    public static void addMember(MockMvc mockMvc, ObjectMapper objectMapper,
                                 String token, long kbId, String username, String permission) throws Exception {
        MvcResult result = mockMvc.perform(post("/kbs/" + kbId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"permission\":\"" + permission + "\"}"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(0, root.path("code").asInt(), root.path("message").asText());
    }

    public static String uniqueName(String prefix) {
        return prefix + (System.nanoTime() % 1_000_000);
    }

    public static void assertApiError(JsonNode root, int expectedCode) {
        assertNotEquals(0, root.path("code").asInt());
        assertEquals(expectedCode, root.path("code").asInt());
    }
}
