package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.entity.WikiPage;
import com.fast.knowledge.mapper.WikiPageMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/kbs/{kbId}/wiki")
public class WikiController {

    private final WikiPageMapper wikiPageMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    public WikiController(WikiPageMapper wikiPageMapper, KnowledgeBaseService knowledgeBaseService) {
        this.wikiPageMapper = wikiPageMapper;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/pages")
    public ApiResponse<List<WikiPage>> list(@PathVariable Long kbId) {
        knowledgeBaseService.checkReadPermission(knowledgeBaseService.getById(kbId));
        List<WikiPage> pages = wikiPageMapper.selectList(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getKbId, kbId)
                .orderByDesc(WikiPage::getUpdatedAt));
        return ApiResponse.ok(pages);
    }

    @GetMapping("/pages/{slug}")
    public ApiResponse<WikiPage> get(@PathVariable Long kbId, @PathVariable String slug) {
        knowledgeBaseService.checkReadPermission(knowledgeBaseService.getById(kbId));
        WikiPage page = wikiPageMapper.findByKbAndSlug(kbId, slug);
        if (page == null) {
            return ApiResponse.fail("Wiki 页面不存在");
        }
        return ApiResponse.ok(page);
    }
}
