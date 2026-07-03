package com.fast.knowledge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.integration.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KbMemberIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void removeMemberValidatesKbScope() throws Exception {
        String adminToken = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);
        String owner = ApiTestSupport.uniqueName("owner");
        String member = ApiTestSupport.uniqueName("member");
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, owner);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, member);

        String ownerToken = ApiTestSupport.login(mockMvc, objectMapper, owner, "pass1234");
        long kbA = ApiTestSupport.createKb(mockMvc, objectMapper, ownerToken, "kb-a");
        long kbB = ApiTestSupport.createKb(mockMvc, objectMapper, ownerToken, "kb-b");
        ApiTestSupport.addMember(mockMvc, objectMapper, ownerToken, kbA, member, "READ");

        MvcResult listResult = mockMvc.perform(get("/kbs/" + kbA + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode members = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data");
        long memberId = members.get(0).path("id").asLong();

        mockMvc.perform(delete("/kbs/" + kbB + "/members/" + memberId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("成员不存在"));
    }
}
