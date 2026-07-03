package com.fast.knowledge.storage;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "knowledge.storage.provider", havingValue = "minio", matchIfMissing = true)
public class MinioStorageProvider implements StorageProvider {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "bat", "cmd", "sh", "ps1", "dll", "so");

    private final KnowledgeProperties.Minio minio;
    private S3Client s3Client;

    public MinioStorageProvider(KnowledgeProperties properties) {
        this.minio = properties.getStorage().getMinio();
    }

    @PostConstruct
    void init() {
        if (minio.getEndpoint() == null || minio.getEndpoint().isBlank()) {
            throw new IllegalStateException("MinIO endpoint 未配置");
        }
        if (minio.getBucket() == null || minio.getBucket().isBlank()) {
            throw new IllegalStateException("MinIO bucket 未配置");
        }
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        var builder = S3Client.builder()
                .endpointOverride(URI.create(minio.getEndpoint()))
                .serviceConfiguration(s3Configuration)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getAccessKey(), minio.getSecretKey())));
        String region = (minio.getRegion() != null && !minio.getRegion().isBlank())
                ? minio.getRegion() : "us-east-1";
        this.s3Client = builder.region(Region.of(region)).build();
    }

    @Override
    public StoredObject storeUpload(Long kbId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = extensionOf(originalName);
        validateExtension(ext);
        String objectKey = objectKey(kbId, UUID.randomUUID() + "." + ext);
        s3Client.putObject(
                PutObjectRequest.builder().bucket(minio.getBucket()).key(objectKey).build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
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
        s3Client.putObject(
                PutObjectRequest.builder().bucket(minio.getBucket()).key(objectKey).build(),
                RequestBody.fromBytes(bytes));
        return new StoredObject(objectKey, ext, bytes.length);
    }

    @Override
    public void delete(String filePath) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(minio.getBucket())
                .key(normalizeKey(filePath))
                .build());
    }

    @Override
    public Path readablePath(String filePath) {
        throw new BusinessException("MinIO 存储不支持本地路径读取，请使用 openInputStream");
    }

    @Override
    public InputStream openInputStream(String filePath) throws IOException {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(minio.getBucket())
                    .key(normalizeKey(filePath))
                    .build());
        } catch (Exception e) {
            throw new IOException("读取 MinIO 对象失败: " + filePath, e);
        }
    }

    private String objectKey(Long kbId, String fileName) {
        String prefix = minio.getPrefix() != null ? minio.getPrefix().trim() : "";
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + kbId + "/" + fileName;
    }

    private String normalizeKey(String filePath) {
        String key = filePath == null ? "" : filePath.trim();
        if (key.startsWith("/")) {
            return key.substring(1);
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
