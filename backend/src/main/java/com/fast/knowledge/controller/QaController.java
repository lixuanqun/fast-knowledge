package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.QaRequest;
import com.fast.knowledge.model.vo.QaResponseVO;
import com.fast.knowledge.service.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/qa")
public class QaController {

    private final RagService ragService;

    public QaController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping
    public ApiResponse<QaResponseVO> ask(@RequestBody QaRequest request) throws Exception {
        return ApiResponse.ok(ragService.ask(request));
    }
}
