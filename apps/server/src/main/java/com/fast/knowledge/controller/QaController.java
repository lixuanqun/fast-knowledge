package com.fast.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.QaRequest;
import com.fast.knowledge.model.vo.QaHistoryVO;
import com.fast.knowledge.model.vo.QaResponseVO;
import com.fast.knowledge.security.RateLimit;
import com.fast.knowledge.service.QaHistoryService;
import com.fast.knowledge.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/qa")
public class QaController {

    private final RagService ragService;
    private final QaHistoryService qaHistoryService;

    public QaController(RagService ragService, QaHistoryService qaHistoryService) {
        this.ragService = ragService;
        this.qaHistoryService = qaHistoryService;
    }

    @PostMapping
    @RateLimit(maxRequests = 20, windowSeconds = 60)
    public ApiResponse<QaResponseVO> ask(@Valid @RequestBody QaRequest request) throws Exception {
        return ApiResponse.ok(ragService.ask(request));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<IPage<QaHistoryVO>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) Long userId) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        return ApiResponse.ok(qaHistoryService.page(page, safeSize, kbId, userId));
    }

    @GetMapping("/history/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportHistory(
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "5000") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20000);
        String csv = qaHistoryService.exportCsv(kbId, userId, safeLimit);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"qa-history.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
