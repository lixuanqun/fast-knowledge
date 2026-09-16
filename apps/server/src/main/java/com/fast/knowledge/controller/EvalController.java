package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.EvalCaseRequest;
import com.fast.knowledge.model.entity.EvalCase;
import com.fast.knowledge.model.entity.EvalDataset;
import com.fast.knowledge.model.entity.EvalRun;
import com.fast.knowledge.model.entity.EvalRunItem;
import com.fast.knowledge.service.EvalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 质量评测（WP3 评测闭环）— 仅管理员。
 */
@RestController
@RequestMapping("/evals")
@PreAuthorize("hasRole('ADMIN')")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    // ---- 数据集 ----

    @GetMapping("/datasets")
    public ApiResponse<List<EvalDataset>> listDatasets(@RequestParam(required = false) Long kbId) {
        return ApiResponse.ok(evalService.listDatasets(kbId));
    }

    @PostMapping("/datasets")
    public ApiResponse<EvalDataset> createDataset(@RequestBody EvalDataset dataset) {
        return ApiResponse.ok(evalService.createDataset(dataset));
    }

    @PutMapping("/datasets/{id}")
    public ApiResponse<EvalDataset> updateDataset(@PathVariable Long id, @RequestBody EvalDataset patch) {
        return ApiResponse.ok(evalService.updateDataset(id, patch));
    }

    @DeleteMapping("/datasets/{id}")
    public ApiResponse<Void> deleteDataset(@PathVariable Long id) {
        evalService.deleteDataset(id);
        return ApiResponse.ok(null);
    }

    // ---- 用例 ----

    @GetMapping("/datasets/{id}/cases")
    public ApiResponse<List<EvalCase>> listCases(@PathVariable Long id) {
        return ApiResponse.ok(evalService.listCases(id));
    }

    @PostMapping("/datasets/{id}/cases")
    public ApiResponse<EvalCase> addCase(@PathVariable Long id, @RequestBody EvalCaseRequest request) {
        return ApiResponse.ok(evalService.addCase(id, request));
    }

    @PutMapping("/cases/{id}")
    public ApiResponse<Void> updateCase(@PathVariable Long id, @RequestBody EvalCaseRequest request) {
        evalService.updateCase(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/cases/{id}")
    public ApiResponse<Void> deleteCase(@PathVariable Long id) {
        evalService.deleteCase(id);
        return ApiResponse.ok(null);
    }

    // ---- 运行 ----

    @PostMapping("/datasets/{id}/runs")
    public ApiResponse<EvalRun> startRun(@PathVariable Long id) {
        return ApiResponse.ok(evalService.startRun(id));
    }

    @GetMapping("/datasets/{id}/runs")
    public ApiResponse<List<EvalRun>> listRuns(@PathVariable Long id) {
        return ApiResponse.ok(evalService.listRuns(id));
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<Map<String, Object>> getRun(@PathVariable Long id) {
        EvalRun run = evalService.getRun(id);
        List<EvalRunItem> items = evalService.listRunItems(id);
        return ApiResponse.ok(Map.of("run", run, "items", items));
    }
}
