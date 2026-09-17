package com.fast.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KgEdge;
import com.fast.knowledge.model.entity.KgEntity;
import com.fast.knowledge.service.KnowledgeGraphService;
import com.fast.knowledge.service.TextExtractionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * WP7 知识图谱管理 API — 仅管理员。
 * 企业版门控预留：正式发布时对 rebuild 加 EditionGuard.requireEnterprise("知识图谱")。
 */
@RestController
@RequestMapping("/kg")
@PreAuthorize("hasRole('ADMIN')")
public class KgController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final DocumentMapper documentMapper;
    private final TextExtractionService textExtractionService;

    public KgController(KnowledgeGraphService knowledgeGraphService,
                        DocumentMapper documentMapper,
                        TextExtractionService textExtractionService) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.documentMapper = documentMapper;
        this.textExtractionService = textExtractionService;
    }

    @GetMapping("/{kbId}/entities")
    public ApiResponse<List<KgEntity>> listEntities(@PathVariable Long kbId,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(knowledgeGraphService.listEntities(kbId, limit));
    }

    @GetMapping("/{kbId}/edges")
    public ApiResponse<List<KgEdge>> listEdges(@PathVariable Long kbId,
                                               @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(knowledgeGraphService.listEdges(kbId, limit));
    }

    /** 全量重建 kb 图谱（清空后逐文档重新抽取，异步执行） */
    @PostMapping("/rebuild/{kbId}")
    public ApiResponse<Map<String, Object>> rebuild(@PathVariable Long kbId) {
        List<KbDocument> docs = documentMapper.selectList(
                Wrappers.<KbDocument>lambdaQuery().eq(KbDocument::getKbId, kbId));
        knowledgeGraphService.deleteByKb(kbId);
        int accepted = 0;
        for (KbDocument doc : docs) {
            try {
                String text = textExtractionService.extractFullText(doc);
                knowledgeGraphService.scheduleBuild(kbId, doc.getId(), doc.getTitle(), text);
                accepted++;
            } catch (Exception e) {
                // 单文档失败跳过（如源文件缺失）
            }
        }
        return ApiResponse.ok(Map.of("accepted", accepted, "message", "KG 重建已在后台执行"));
    }
}
