package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.vo.DocumentChunkVO;
import com.fast.knowledge.model.vo.DocumentPreviewVO;
import com.fast.knowledge.service.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/kbs/{kbId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ApiResponse<List<KbDocument>> list(@PathVariable Long kbId) {
        return ApiResponse.ok(documentService.listByKb(kbId));
    }

    @GetMapping("/{docId}")
    public ApiResponse<KbDocument> get(@PathVariable Long kbId, @PathVariable Long docId) {
        return ApiResponse.ok(documentService.getById(kbId, docId));
    }

    @GetMapping("/{docId}/preview")
    public ApiResponse<DocumentPreviewVO> preview(@PathVariable Long kbId, @PathVariable Long docId) throws Exception {
        return ApiResponse.ok(documentService.preview(kbId, docId));
    }

    @GetMapping("/{docId}/chunks")
    public ApiResponse<List<DocumentChunkVO>> chunks(@PathVariable Long kbId, @PathVariable Long docId) {
        return ApiResponse.ok(documentService.listChunks(kbId, docId));
    }

    @PostMapping("/upload")
    public ApiResponse<KbDocument> upload(@PathVariable Long kbId,
                                          @RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(documentService.upload(kbId, file));
    }

    @DeleteMapping("/{docId}")
    public ApiResponse<Void> delete(@PathVariable Long kbId, @PathVariable Long docId) {
        documentService.delete(kbId, docId);
        return ApiResponse.ok();
    }

    @PostMapping("/{docId}/reindex")
    public ApiResponse<Void> reindex(@PathVariable Long kbId, @PathVariable Long docId) {
        documentService.reindex(kbId, docId);
        return ApiResponse.ok();
    }
}
