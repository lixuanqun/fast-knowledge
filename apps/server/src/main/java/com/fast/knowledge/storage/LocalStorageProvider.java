package com.fast.knowledge.storage;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 本地目录存储 — 单机极简部署（provider=local），零外部对象存储依赖。
 *
 * <p>StoredObject.absolutePath 保存的是对象键（如 {@code knowledge/12/uuid.pdf}），
 * 与 MinIO/OSS 的 key 形态一致：数据库不绑机器路径，换 provider 时同步文件目录即可迁移。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.storage.provider", havingValue = "local")
public class LocalStorageProvider implements StorageProvider {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "bat", "cmd", "sh", "ps1", "dll", "so");

    private final Path baseDir;
    private final String prefix;

    public LocalStorageProvider(KnowledgeProperties properties) {
        KnowledgeProperties.LocalStorage local = properties.getStorage().getLocal();
        this.baseDir = Paths.get(local.getBaseDir()).toAbsolutePath().normalize();
        String prefix = local.getPrefix() == null ? "" : local.getPrefix().trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        this.prefix = prefix;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("本地存储目录不可创建: " + baseDir, e);
        }
        log.info("本地文件存储已启用: {}（前缀 {}）", baseDir, prefix);
    }

    @Override
    public StoredObject storeUpload(Long kbId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String ext = extensionOf(file.getOriginalFilename());
        validateExtension(ext);
        String objectKey = objectKey(kbId, UUID.randomUUID() + "." + ext);
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());
        return new StoredObject(objectKey, ext, file.getSize());
    }

    @Override
    public StoredObject storeText(Long kbId, String fileName, String content) throws IOException {
        String ext = extensionOf(fileName);
        if (!"md".equals(ext) && !"txt".equals(ext)) {
            ext = "md";
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String objectKey = objectKey(kbId, UUID.randomUUID() + "." + ext);
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return new StoredObject(objectKey, ext, bytes.length);
    }

    @Override
    public void delete(String filePath) throws IOException {
        Files.deleteIfExists(resolve(normalizeKey(filePath)));
    }

    @Override
    public Path readablePath(String filePath) {
        return resolve(normalizeKey(filePath));
    }

    @Override
    public InputStream openInputStream(String filePath) throws IOException {
        return Files.newInputStream(resolve(normalizeKey(filePath)));
    }

    @Override
    public String storeAsset(String objectKey, byte[] bytes) {
        String key = normalizeKey(objectKey);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new BusinessException("写入存储资产失败: " + objectKey, e);
        }
        return key;
    }

    @Override
    public byte[] getAsset(String objectKey) {
        Path target = resolve(normalizeKey(objectKey));
        if (!Files.exists(target)) {
            return null;
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BusinessException("读取存储资产失败: " + objectKey, e);
        }
    }

    /** 对象键 → 绝对路径，统一做目录穿越防护（.. / 绝对路径注入） */
    private Path resolve(String key) {
        Path path = baseDir.resolve(key).normalize();
        if (!path.startsWith(baseDir)) {
            throw new BusinessException("非法文件路径");
        }
        return path;
    }

    private String objectKey(Long kbId, String fileName) {
        return prefix + kbId + "/" + fileName;
    }

    private String normalizeKey(String filePath) {
        String key = filePath == null ? "" : filePath.trim();
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (!prefix.isEmpty() && !key.startsWith(prefix)) {
            // 兼容无前缀的裸键（历史数据/调用方自构 key）
            return prefix + key;
        }
        return key;
    }

    private String extensionOf(String name) {
        if (name == null || !name.contains(".")) {
            return "txt";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }

    private void validateExtension(String ext) {
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不允许上传该类型文件: ." + ext);
        }
    }
}
