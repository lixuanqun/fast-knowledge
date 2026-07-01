package com.fast.knowledge.storage;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "knowledge.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageProvider implements StorageProvider {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "bat", "cmd", "sh", "ps1", "dll", "so");

    private final Path uploadRoot;

    public LocalFileStorageProvider(KnowledgeProperties properties) {
        this.uploadRoot = Paths.get(properties.getStorage().getUploadDir()).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject storeUpload(Long kbId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = extensionOf(originalName);
        validateExtension(ext);
        String storedName = UUID.randomUUID() + "." + ext;
        Path target = kbDir(kbId).resolve(storedName);
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());
        return new StoredObject(target.toString(), ext, file.getSize());
    }

    @Override
    public StoredObject storeText(Long kbId, String fileName, String content) throws IOException {
        String ext = extensionOf(fileName);
        if (!"md".equals(ext) && !"txt".equals(ext)) {
            ext = "md";
        }
        String storedName = UUID.randomUUID() + "." + ext;
        Path target = kbDir(kbId).resolve(storedName);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return new StoredObject(target.toString(), ext, content.length());
    }

    @Override
    public void delete(String filePath) throws IOException {
        Files.deleteIfExists(readablePath(filePath));
    }

    @Override
    public Path readablePath(String filePath) {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot)) {
            throw new BusinessException("非法文件路径");
        }
        return path;
    }

    private Path kbDir(Long kbId) {
        return uploadRoot.resolve(String.valueOf(kbId));
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
