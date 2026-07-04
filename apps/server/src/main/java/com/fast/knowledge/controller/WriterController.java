package com.fast.knowledge.controller;

import com.fast.knowledge.model.dto.SaveDocumentRequest;
import com.fast.knowledge.model.dto.WriterRequest;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.security.RateLimit;
import com.fast.knowledge.service.DocumentService;
import com.fast.knowledge.service.WriterService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/writer")
public class WriterController {

    private final WriterService writerService;
    private final DocumentService documentService;

    public WriterController(WriterService writerService, DocumentService documentService) {
        this.writerService = writerService;
        this.documentService = documentService;
    }

    @RateLimit(maxRequests = 10, windowSeconds = 60)
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@Valid @RequestBody WriterRequest request) {
        return writerService.generate(request);
    }

    @PostMapping("/save")
    public com.fast.knowledge.common.ApiResponse<KbDocument> save(@Valid @RequestBody SaveDocumentRequest request) throws Exception {
        return com.fast.knowledge.common.ApiResponse.ok(
                documentService.saveTextDocument(request.getKbId(), request.getTitle(), request.getContent()));
    }
}
