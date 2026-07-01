package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.SearchRequest;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.service.SearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public ApiResponse<List<SearchHitVO>> search(@Valid @RequestBody SearchRequest request) throws Exception {
        return ApiResponse.ok(searchService.search(request));
    }
}
