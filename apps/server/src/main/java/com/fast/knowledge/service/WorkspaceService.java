package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.WorkspaceMapper;
import com.fast.knowledge.model.entity.Workspace;
import com.fast.knowledge.model.vo.WorkspaceVO;
import com.fast.knowledge.security.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;

    public WorkspaceService(WorkspaceMapper workspaceMapper) {
        this.workspaceMapper = workspaceMapper;
    }

    public Workspace getDefault() {
        return workspaceMapper.findDefault();
    }

    public List<Workspace> listMine(Long userId) {
        return workspaceMapper.findByOwnerOrMember(userId);
    }

    public List<WorkspaceVO> listMineVo(Long userId) {
        return listMine(userId).stream().map(this::toVo).toList();
    }

    public WorkspaceVO getVoById(Long id, Long userId) {
        Workspace ws = workspaceMapper.selectById(id);
        if (ws == null) {
            throw new BusinessException("工作区不存在");
        }
        UserContext ctx = UserContext.get();
        if (!ws.getOwnerId().equals(userId) && (ctx == null || !"ADMIN".equals(ctx.getRole()))) {
            throw new BusinessException(403, "无权限访问该工作区");
        }
        return toVo(ws);
    }

    public Workspace createDefaultForUser(Long userId) {
        Workspace ws = new Workspace();
        ws.setName("默认工作区");
        ws.setOwnerId(userId);
        workspaceMapper.insert(ws);
        return ws;
    }

    private WorkspaceVO toVo(Workspace ws) {
        WorkspaceVO vo = new WorkspaceVO();
        vo.setId(ws.getId());
        vo.setName(ws.getName());
        vo.setOwnerId(ws.getOwnerId());
        vo.setCreatedAt(ws.getCreatedAt());
        vo.setUpdatedAt(ws.getUpdatedAt());
        return vo;
    }
}
