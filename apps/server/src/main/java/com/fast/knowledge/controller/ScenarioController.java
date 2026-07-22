package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.EditionGuard;
import com.fast.knowledge.model.vo.ScenarioTemplateVO;
import com.fast.knowledge.service.ScenarioTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/scenarios")
@PreAuthorize("hasRole('ADMIN')")
public class ScenarioController {

    private final ScenarioTemplateService scenarioTemplateService;
    private final EditionGuard editionGuard;

    public ScenarioController(ScenarioTemplateService scenarioTemplateService, EditionGuard editionGuard) {
        this.scenarioTemplateService = scenarioTemplateService;
        this.editionGuard = editionGuard;
    }

    @GetMapping
    public ApiResponse<List<ScenarioTemplateVO>> list() {
        editionGuard.requireEnterprise("制造场景模板");
        return ApiResponse.ok(scenarioTemplateService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ScenarioTemplateVO> get(@PathVariable String id) {
        editionGuard.requireEnterprise("制造场景模板");
        ScenarioTemplateVO vo = scenarioTemplateService.getById(id);
        if (vo == null) {
            throw new BusinessException("场景模板不存在");
        }
        return ApiResponse.ok(vo);
    }
}
