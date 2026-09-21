package com.fast.knowledge.storage;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStorageProviderTest {

    @TempDir
    Path tempDir;

    private LocalStorageProvider provider;

    @BeforeEach
    void setUp() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getStorage().getLocal().setBaseDir(tempDir.toString());
        properties.getStorage().getLocal().setPrefix("knowledge/");
        provider = new LocalStorageProvider(properties);
    }

    @Test
    void storeUploadWritesFileUnderBaseDirAndReturnsPortableKey() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "手册.pdf", "application/pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8));

        StoredObject stored = provider.storeUpload(12L, file);

        assertTrue(stored.absolutePath().startsWith("knowledge/12/"));
        assertEquals("pdf", stored.extension());
        assertEquals(file.getSize(), stored.size());
        // 键形态与 MinIO 一致，不落机器绝对路径
        assertFalse(stored.absolutePath().contains(tempDir.toString()));
        try (var in = provider.openInputStream(stored.absolutePath())) {
            assertEquals("pdf-bytes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void storeTextForcesMdOrTxtExtension() throws IOException {
        StoredObject stored = provider.storeText(1L, "笔记.docx", "正文");

        assertEquals("md", stored.extension());
        assertTrue(provider.readablePath(stored.absolutePath()).getFileName().toString().endsWith(".md"));
    }

    @Test
    void deleteRemovesFile() throws IOException {
        StoredObject stored = provider.storeText(3L, "a.md", "内容");

        provider.delete(stored.absolutePath());

        assertFalse(Files.exists(provider.readablePath(stored.absolutePath())));
    }

    @Test
    void pathTraversalIsRejected() {
        assertThrows(BusinessException.class, () -> provider.readablePath("knowledge/../../escape.txt"));
        assertThrows(BusinessException.class, () -> provider.readablePath("../../../escape.txt"));
        // 绝对路径注入被去根化，仍限制在 baseDir 内
        Path neutralized = provider.readablePath("/etc/passwd");
        assertTrue(neutralized.startsWith(tempDir));
    }

    @Test
    void assetRoundtripAndMissingReturnsNull() {
        String key = provider.storeAsset("knowledge/assets/generated/logo.png", "img".getBytes(StandardCharsets.UTF_8));

        assertEquals("knowledge/assets/generated/logo.png", key);
        assertArrayEquals("img".getBytes(StandardCharsets.UTF_8), provider.getAsset(key));
        assertNull(provider.getAsset("knowledge/assets/generated/missing.png"));
    }

    @Test
    void blockedExtensionsAreRejected() {
        MockMultipartFile exe = new MockMultipartFile(
                "file", "恶意.exe", "application/octet-stream", new byte[] {1});

        assertThrows(BusinessException.class, () -> provider.storeUpload(1L, exe));
    }

    @Test
    void emptyUploadIsRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "空.txt", "text/plain", new byte[0]);

        assertThrows(BusinessException.class, () -> provider.storeUpload(1L, empty));
    }
}
