package com.fast.knowledge.service;

import com.fast.knowledge.ai.port.VisionPort;
import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * 视觉问答 — 图片上传校验后交由视觉模型分析。
 * 内网纯离线模式不可用（qwen-vl 仅云端），由 allow-external 语义约束。
 */
@Slf4j
@Service
public class VisionService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/bmp");

    private final VisionPort visionPort;
    private final KnowledgeProperties properties;
    private final AuditLogService auditLogService;

    public VisionService(VisionPort visionPort, KnowledgeProperties properties, AuditLogService auditLogService) {
        this.visionPort = visionPort;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    public String ask(MultipartFile image, String question) {
        KnowledgeProperties.Vision vision = properties.getVision();
        if (!vision.isEnabled()) {
            throw new BusinessException("图片问答功能未启用");
        }
        if (!properties.getLlm().isAllowExternal()) {
            throw new BusinessException("内网纯离线模式下图片问答不可用（视觉模型仅云端提供）");
        }
        if (image == null || image.isEmpty()) {
            throw new BusinessException("请上传图片");
        }
        if (question == null || question.isBlank()) {
            throw new BusinessException("请输入问题");
        }
        String mimeType = resolveMimeType(image.getContentType());
        long maxBytes = Math.max(1, vision.getMaxImageMb()) * 1024L * 1024L;
        if (image.getSize() > maxBytes) {
            throw new BusinessException("图片超过大小限制 " + vision.getMaxImageMb() + "MB");
        }

        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(image.getBytes());
        } catch (IOException e) {
            throw new BusinessException("图片读取失败，请重试");
        }

        String answer = visionPort.askAboutImage(base64, mimeType, question.trim());
        auditLogService.log(AuditActions.VISION_ASK, "IMAGE", null,
                "type=" + mimeType + ", size=" + image.getSize() + ", question=" + truncate(question, 100));
        return answer;
    }

    private String resolveMimeType(String contentType) {
        if (contentType == null) {
            throw new BusinessException("仅支持 JPG/PNG/WEBP/BMP 图片");
        }
        String type = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException("仅支持 JPG/PNG/WEBP/BMP 图片，当前: " + type);
        }
        return type;
    }

    private static String truncate(String s, int len) {
        return s.length() <= len ? s : s.substring(0, len) + "...";
    }
}
