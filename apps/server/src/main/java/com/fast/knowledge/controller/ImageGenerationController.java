package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.security.RateLimit;
import com.fast.knowledge.service.ImageGenerationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/image-gen")
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

    public ImageGenerationController(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }

    public record SubmitRequest(String prompt) {
    }

    @RateLimit(maxRequests = 5, windowSeconds = 60)
    @PostMapping("/tasks")
    public ApiResponse<Map<String, String>> submit(@org.springframework.web.bind.annotation.RequestBody SubmitRequest request) {
        String taskId = imageGenerationService.submit(request.prompt());
        return ApiResponse.ok(Map.of("taskId", taskId));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<ImageGenerationService.ImageTask> query(@PathVariable String taskId) {
        return ApiResponse.ok(imageGenerationService.query(taskId));
    }

    /** 代理下载生成的图片（DashScope 临时链接不直接暴露给前端） */
    @GetMapping("/tasks/{taskId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String taskId) {
        byte[] bytes = imageGenerationService.download(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fk-image-" + taskId + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes);
    }
}
