package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.KbMemberRequest;
import com.fast.knowledge.model.entity.KbMember;
import com.fast.knowledge.service.KbMemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kbs/{kbId}/members")
public class KbMemberController {

    private final KbMemberService kbMemberService;

    public KbMemberController(KbMemberService kbMemberService) {
        this.kbMemberService = kbMemberService;
    }

    @GetMapping
    public ApiResponse<List<KbMember>> list(@PathVariable Long kbId) {
        return ApiResponse.ok(kbMemberService.listMembers(kbId));
    }

    @PostMapping
    public ApiResponse<KbMember> add(@PathVariable Long kbId, @Valid @RequestBody KbMemberRequest request) {
        return ApiResponse.ok(kbMemberService.addMember(kbId, request));
    }

    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> remove(@PathVariable Long kbId, @PathVariable Long memberId) {
        kbMemberService.removeMember(kbId, memberId);
        return ApiResponse.ok();
    }
}
