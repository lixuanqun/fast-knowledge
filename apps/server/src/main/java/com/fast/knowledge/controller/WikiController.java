package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.entity.WikiPage;
import com.fast.knowledge.service.WikiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/kbs/{kbId}/wiki")
public class WikiController {

    private final WikiService wikiService;

    public WikiController(WikiService wikiService) {
        this.wikiService = wikiService;
    }

    @GetMapping("/pages")
    public ApiResponse<List<WikiPage>> list(@PathVariable Long kbId,
                                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(wikiService.list(kbId, status));
    }

    @GetMapping("/pages/{slug}")
    public ApiResponse<WikiPage> get(@PathVariable Long kbId, @PathVariable String slug) {
        return ApiResponse.ok(wikiService.getBySlug(kbId, slug));
    }

    @PostMapping("/pages/{pageId}/publish")
    public ApiResponse<WikiPage> publish(@PathVariable Long kbId, @PathVariable Long pageId) {
        return ApiResponse.ok(wikiService.publish(kbId, pageId));
    }

    @PostMapping("/pages/{pageId}/reject")
    public ApiResponse<WikiPage> reject(@PathVariable Long kbId, @PathVariable Long pageId) {
        return ApiResponse.ok(wikiService.reject(kbId, pageId));
    }

    @PostMapping("/index/rebuild")
    public ApiResponse<WikiPage> rebuildIndex(@PathVariable Long kbId) {
        // 复用写权限：通过 publish 路径的 check 在 rebuild 前先 getById+checkWrite
        return ApiResponse.ok(wikiService.rebuildIndex(kbId));
    }
}
