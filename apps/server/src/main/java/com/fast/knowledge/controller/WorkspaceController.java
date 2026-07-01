package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.vo.WorkspaceVO;
import com.fast.knowledge.security.UserContext;
import com.fast.knowledge.service.WorkspaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResponse<List<WorkspaceVO>> listMine() {
        return ApiResponse.ok(workspaceService.listMineVo(UserContext.currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkspaceVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(workspaceService.getVoById(id, UserContext.currentUserId()));
    }
}
