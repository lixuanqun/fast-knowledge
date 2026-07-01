package com.fast.knowledge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.integration.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeBaseAclIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readMemberCanViewKb() throws Exception {
        String adminToken = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);
        String owner = ApiTestSupport.uniqueName("owner");
        String reader = ApiTestSupport.uniqueName("reader");
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, owner);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, reader);

        String ownerToken = ApiTestSupport.login(mockMvc, objectMapper, owner, "pass1234");
        long kbId = ApiTestSupport.createKb(mockMvc, objectMapper, ownerToken, "acl-read-kb");
        ApiTestSupport.addMember(mockMvc, objectMapper, ownerToken, kbId, reader, "READ");

        String readerToken = ApiTestSupport.login(mockMvc, objectMapper, reader, "pass1234");
        mockMvc.perform(get("/kbs/" + kbId)
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("acl-read-kb"));
    }

    @Test
    void readMemberCannotUpdateKb() throws Exception {
        String adminToken = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);
        String owner = ApiTestSupport.uniqueName("owner");
        String reader = ApiTestSupport.uniqueName("reader");
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, owner);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, reader);

        String ownerToken = ApiTestSupport.login(mockMvc, objectMapper, owner, "pass1234");
        long kbId = ApiTestSupport.createKb(mockMvc, objectMapper, ownerToken, "acl-no-write");
        ApiTestSupport.addMember(mockMvc, objectMapper, ownerToken, kbId, reader, "READ");

        String readerToken = ApiTestSupport.login(mockMvc, objectMapper, reader, "pass1234");
        mockMvc.perform(put("/kbs/" + kbId)
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hacked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void writeMemberCannotDeleteKb() throws Exception {
        String adminToken = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);
        String owner = ApiTestSupport.uniqueName("owner");
        String writer = ApiTestSupport.uniqueName("writer");
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, owner);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, writer);

        String ownerToken = ApiTestSupport.login(mockMvc, objectMapper, owner, "pass1234");
        long kbId = ApiTestSupport.createKb(mockMvc, objectMapper, ownerToken, "acl-no-delete");
        ApiTestSupport.addMember(mockMvc, objectMapper, ownerToken, kbId, writer, "WRITE");

        String writerToken = ApiTestSupport.login(mockMvc, objectMapper, writer, "pass1234");
        mockMvc.perform(delete("/kbs/" + kbId)
                        .header("Authorization", "Bearer " + writerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void writeMemberCannotManageMembers() throws Exception {
        String adminToken = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);
        String owner = ApiTestSupport.uniqueName("owner");
        String writer = ApiTestSupport.uniqueName("writer");
        String target = ApiTestSupport.uniqueName("target");
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, owner);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, writer);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, target);

        String ownerToken = ApiTestSupport.login(mockMvc, objectMapper, owner, "pass1234");
        long kbId = ApiTestSupport.createKb(mockMvc, objectMapper, ownerToken, "acl-no-member-mgmt");
        ApiTestSupport.addMember(mockMvc, objectMapper, ownerToken, kbId, writer, "WRITE");

        String writerToken = ApiTestSupport.login(mockMvc, objectMapper, writer, "pass1234");
        mockMvc.perform(post("/kbs/" + kbId + "/members")
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + target + "\",\"permission\":\"READ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void kbAdminMemberCanManageMembers() throws Exception {
        String adminToken = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);
        String owner = ApiTestSupport.uniqueName("owner");
        String kbAdmin = ApiTestSupport.uniqueName("kbadmin");
        String target = ApiTestSupport.uniqueName("target");
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, owner);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, kbAdmin);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, target);

        String ownerToken = ApiTestSupport.login(mockMvc, objectMapper, owner, "pass1234");
        long kbId = ApiTestSupport.createKb(mockMvc, objectMapper, ownerToken, "acl-kb-admin");
        ApiTestSupport.addMember(mockMvc, objectMapper, ownerToken, kbId, kbAdmin, "ADMIN");

        String kbAdminToken = ApiTestSupport.login(mockMvc, objectMapper, kbAdmin, "pass1234");
        mockMvc.perform(post("/kbs/" + kbId + "/members")
                        .header("Authorization", "Bearer " + kbAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + target + "\",\"permission\":\"READ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(target))
                .andExpect(jsonPath("$.data.displayName").value(target));
    }

    @Test
    void rebuildIndexRequiresWritePermission() throws Exception {
        String adminToken = ApiTestSupport.loginAsAdmin(mockMvc, objectMapper);
        String owner = ApiTestSupport.uniqueName("owner");
        String reader = ApiTestSupport.uniqueName("reader");
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, owner);
        ApiTestSupport.createUser(mockMvc, objectMapper, adminToken, reader);

        String ownerToken = ApiTestSupport.login(mockMvc, objectMapper, owner, "pass1234");
        long kbId = ApiTestSupport.createKb(mockMvc, objectMapper, ownerToken, "acl-rebuild");
        ApiTestSupport.addMember(mockMvc, objectMapper, ownerToken, kbId, reader, "READ");

        String readerToken = ApiTestSupport.login(mockMvc, objectMapper, reader, "pass1234");
        mockMvc.perform(post("/index-tasks/rebuild/" + kbId)
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }
}
