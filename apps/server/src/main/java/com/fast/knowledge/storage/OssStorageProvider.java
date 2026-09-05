package com.fast.knowledge.storage;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阿里云 OSS 存储（S3 兼容协议，虚拟主机风格 bucket.endpoint）。
 * 与 MinIO 实现同构，按 knowledge.storage.provider=minio|oss 切换；
 * 面向阿里云部署形态，生成图片等资产可直接落 OSS。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.storage.provider", havingValue = "oss")
public class OssStorageProvider implements StorageProvider {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "bat", "cmd", "sh", "ps1", "dll", "so");
    private static final Pattern OSS_REGION = Pattern.compile("oss-([a-z0-9-]+)");

    private final KnowledgeProperties.Oss oss;
    private S3Client s3Client;

    public OssStorageProvider(KnowledgeProperties properties) {
        this.oss = properties.getStorage().getOss();
    }

    @PostConstruct
    void init() {
        if (oss.getEndpoint() == null || oss.getEndpoint().isBlank()) {
            throw new IllegalStateException("OSS endpoint 未配置");
        }
        if (oss.getBucket() == null || oss.getBucket().isBlank()) {
            throw new IllegalStateException("OSS bucket 未配置");
        }
        if (oss.getAccessKey() == null || oss.getAccessKey().isBlank()
                || oss.getSecretKey() == null || oss.getSecretKey().isBlank()) {
            throw new IllegalStateException("OSS AccessKey/SecretKey 未配置");
        }
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(false)
                .build();
        var builder = S3Client.builder()
                .endpointOverride(URI.create(oss.getEndpoint()))
                .serviceConfiguration(s3Configuration)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(oss.getAccessKey(), oss.getSecretKey())));
        builder.region(Region.of(deriveRegion(oss.getEndpoint(), oss.getRegion())));
        this.s3Client = builder.build();
        ensureBucket();
    }

    /** bucket 缺失时自动创建（使用 S3 兼容协议，无需控制台预建） */
    private void ensureBucket() {
        try {
            s3Client.headBucket(b -> b.bucket(oss.getBucket()));
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(oss.getBucket()).build());
            log.info("OSS bucket 已自动创建: {}", oss.getBucket());
        }
    }

    /** 签名区域：优先配置，否则从 OSS 端点推断（如 oss-cn-hangzhou → cn-hangzhou） */
    private static String deriveRegion(String endpoint, String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        Matcher m = OSS_REGION.matcher(endpoint);
        if (m.find()) {
            return m.group(1);
        }
        return "us-east-1";
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
                PutObjectRequest.builder().bucket(oss.getBucket()).key(objectKey).build(),
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
                PutObjectRequest.builder().bucket(oss.getBucket()).key(objectKey).build(),
                RequestBody.fromBytes(bytes));
        return new StoredObject(objectKey, ext, bytes.length);
    }

    @Override
    public void delete(String filePath) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(oss.getBucket())
                .key(normalizeKey(filePath))
                .build());
    }

    @Override
    public Path readablePath(String filePath) {
        throw new BusinessException("OSS 存储不支持本地路径读取，请使用 openInputStream");
    }

    @Override
    public InputStream openInputStream(String filePath) throws IOException {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(oss.getBucket())
                    .key(normalizeKey(filePath))
                    .build());
        } catch (Exception e) {
            throw new IOException("读取 OSS 对象失败: " + filePath, e);
        }
    }

    @Override
    public String storeAsset(String objectKey, byte[] bytes) {
        String key = normalizeKey(objectKey);
        s3Client.putObject(
                PutObjectRequest.builder().bucket(oss.getBucket()).key(key).build(),
                RequestBody.fromBytes(bytes));
        return key;
    }

    @Override
    public byte[] getAsset(String objectKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(oss.getBucket())
                    .key(normalizeKey(objectKey))
                    .build()).asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            throw new BusinessException("读取存储资产失败: " + objectKey, e);
        }
    }

    private String objectKey(Long kbId, String fileName) {
        String prefix = oss.getPrefix() != null ? oss.getPrefix().trim() : "";
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
