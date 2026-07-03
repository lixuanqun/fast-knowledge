package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.DocumentMetadataRequest;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.vo.DocumentChunkVO;
import com.fast.knowledge.model.vo.DocumentPreviewVO;
import com.fast.knowledge.service.DocumentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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
    public ApiResponse<DocumentPreviewVO> preview(@PathVariable Long kbId,
                                                  @PathVariable Long docId,
                                                  @RequestParam(required = false) Long chunkId) throws Exception {
        return ApiResponse.ok(documentService.preview(kbId, docId, chunkId));
    }

    @GetMapping("/{docId}/chunks")
    public ApiResponse<List<DocumentChunkVO>> chunks(@PathVariable Long kbId, @PathVariable Long docId) {
        return ApiResponse.ok(documentService.listChunks(kbId, docId));
    }

    @PostMapping("/upload")
    public ApiResponse<KbDocument> upload(@PathVariable Long kbId,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false) String docType,
                                          @RequestParam(required = false) String docNo,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expireDate,
                                          @RequestParam(required = false) String department,
                                          @RequestParam(required = false) String tags) throws Exception {
        DocumentMetadataRequest metadata = new DocumentMetadataRequest();
        metadata.setDocType(docType);
        metadata.setDocNo(docNo);
        metadata.setEffectiveDate(effectiveDate);
        metadata.setExpireDate(expireDate);
        metadata.setDepartment(department);
        metadata.setTags(tags);
        return ApiResponse.ok(documentService.upload(kbId, file, metadata));
    }

    @PutMapping("/{docId}/metadata")
    public ApiResponse<KbDocument> updateMetadata(@PathVariable Long kbId,
                                                  @PathVariable Long docId,
                                                  @RequestBody DocumentMetadataRequest metadata) {
        return ApiResponse.ok(documentService.updateMetadata(kbId, docId, metadata));
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
