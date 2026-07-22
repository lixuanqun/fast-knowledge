package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.storage.StoredObject;
import com.fast.knowledge.storage.StorageProvider;
import com.fast.knowledge.langchain4j.store.KbVectorIndexService;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.dto.DocumentMetadataRequest;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.vo.DocumentChunkVO;
import com.fast.knowledge.model.vo.DocumentPreviewVO;
import com.fast.knowledge.security.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentIngestService documentIngestService;
    private final KbVectorIndexService vectorIndexService;
    private final StorageProvider storageProvider;
    private final AuditLogService auditLogService;
    private final SearchCacheService searchCacheService;
    private final TextExtractionService textExtractionService;
    private final IndexEventPublisher indexEventPublisher;

    public DocumentService(DocumentMapper documentMapper,
                           DocumentChunkMapper documentChunkMapper,
                           IndexTaskMapper indexTaskMapper,
                           KnowledgeBaseService knowledgeBaseService,
                           DocumentIngestService documentIngestService,
                           KbVectorIndexService vectorIndexService,
                           StorageProvider storageProvider,
                           AuditLogService auditLogService,
                           SearchCacheService searchCacheService,
                           TextExtractionService textExtractionService,
                           IndexEventPublisher indexEventPublisher) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentIngestService = documentIngestService;
        this.vectorIndexService = vectorIndexService;
        this.storageProvider = storageProvider;
        this.auditLogService = auditLogService;
        this.searchCacheService = searchCacheService;
        this.textExtractionService = textExtractionService;
        this.indexEventPublisher = indexEventPublisher;
    }

    public List<KbDocument> listByKb(Long kbId) {
        knowledgeBaseService.getById(kbId);
        return documentMapper.findByKbId(kbId);
    }

    public KbDocument getById(Long kbId, Long docId) {
        KbDocument doc = requireDocument(kbId, docId);
        knowledgeBaseService.getById(kbId);
        return doc;
    }

    public DocumentPreviewVO preview(Long kbId, Long docId, Long highlightChunkId) throws Exception {
        KbDocument doc = requireDocument(kbId, docId);
        knowledgeBaseService.getById(kbId);

        DocumentPreviewVO vo = new DocumentPreviewVO();
        vo.setDocumentId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setFileType(doc.getFileType());
        vo.setDocNo(doc.getDocNo());
        vo.setDocType(doc.getDocType());

        TextExtractionService.ExtractedPreview preview = textExtractionService.extractPreview(doc);
        vo.setPreviewMode(preview.mode());
        vo.setContentLength(preview.totalLength());
        vo.setContent(preview.content());
        vo.setTruncated(preview.truncated());

        if (highlightChunkId != null) {
            documentChunkMapper.findByDocumentId(docId).stream()
                    .filter(c -> c.getId().equals(highlightChunkId))
                    .findFirst()
                    .ifPresent(c -> vo.setHighlightSnippet(c.getContent()));
        }
        return vo;
    }

    @Transactional
    public KbDocument updateMetadata(Long kbId, Long docId, DocumentMetadataRequest metadata) {
        KbDocument doc = requireDocument(kbId, docId);
        knowledgeBaseService.checkWritePermission(knowledgeBaseService.getById(kbId));
        applyMetadata(doc, metadata);
        // 允许将日期清空为 null（默认 updateById 会跳过 null 字段）
        documentMapper.update(null, Wrappers.<KbDocument>lambdaUpdate()
                .eq(KbDocument::getId, docId)
                .set(KbDocument::getDocType, doc.getDocType())
                .set(KbDocument::getDocNo, doc.getDocNo())
                .set(KbDocument::getEffectiveDate, doc.getEffectiveDate())
                .set(KbDocument::getExpireDate, doc.getExpireDate())
                .set(KbDocument::getDepartment, doc.getDepartment())
                .set(KbDocument::getTags, doc.getTags())
                .set(KbDocument::getEnabled, doc.getEnabled()));
        searchCacheService.invalidateForKb(kbId);
        return documentMapper.selectById(docId);
    }

    private void applyMetadata(KbDocument doc, DocumentMetadataRequest metadata) {
        if (metadata == null) {
            return;
        }
        if (metadata.getDocType() != null) {
            doc.setDocType(metadata.getDocType().isBlank() ? null : metadata.getDocType().trim());
        }
        if (metadata.getDocNo() != null) {
            doc.setDocNo(metadata.getDocNo().isBlank() ? null : metadata.getDocNo().trim());
        }
        if (Boolean.TRUE.equals(metadata.getClearEffectiveDate())) {
            doc.setEffectiveDate(null);
        } else if (metadata.getEffectiveDate() != null) {
            doc.setEffectiveDate(metadata.getEffectiveDate());
        }
        if (Boolean.TRUE.equals(metadata.getClearExpireDate())) {
            doc.setExpireDate(null);
        } else if (metadata.getExpireDate() != null) {
            doc.setExpireDate(metadata.getExpireDate());
        }
        if (metadata.getDepartment() != null) {
            doc.setDepartment(metadata.getDepartment().isBlank() ? null : metadata.getDepartment().trim());
        }
        if (metadata.getTags() != null) {
            doc.setTags(metadata.getTags().isBlank() ? null : metadata.getTags().trim());
        }
        if (metadata.getEnabled() != null) {
            if (metadata.getEnabled() != 0 && metadata.getEnabled() != 1) {
                throw new BusinessException("enabled 只能为 0 或 1");
            }
            doc.setEnabled(metadata.getEnabled());
        }
    }

    public List<DocumentChunkVO> listChunks(Long kbId, Long docId) {
        requireDocument(kbId, docId);
        knowledgeBaseService.getById(kbId);
        return documentChunkMapper.findByDocumentId(docId).stream()
                .map(this::toChunkVO)
                .collect(Collectors.toList());
    }

    private KbDocument requireDocument(Long kbId, Long docId) {
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new BusinessException("文档不存在");
        }
        return doc;
    }

    private DocumentChunkVO toChunkVO(DocumentChunk chunk) {
        DocumentChunkVO vo = new DocumentChunkVO();
        vo.setId(chunk.getId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setTokenCount(chunk.getTokenCount());
        vo.setSectionTitle(chunk.getSectionTitle());
        return vo;
    }

    @Transactional
    public KbDocument upload(Long kbId, MultipartFile file, DocumentMetadataRequest metadata) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        String originalName = file.getOriginalFilename();
        StoredObject stored = storageProvider.storeUpload(kbId, file);

        KbDocument doc = new KbDocument();
        doc.setKbId(kbId);
        doc.setTitle(stripExtension(originalName));
        doc.setFileName(originalName);
        doc.setFileType(stored.extension());
        doc.setFileSize(stored.size());
        doc.setFilePath(stored.absolutePath());
        doc.setIndexStatus("PENDING");
        doc.setChunkCount(0);
        doc.setEnabled(1);
        doc.setCreatedBy(UserContext.currentUserId());
        applyMetadata(doc, metadata);
        documentMapper.insert(doc);

        IndexTask task = new IndexTask();
        task.setDocumentId(doc.getId());
        task.setStatus("PENDING");
        task.setRetryCount(0);
        indexTaskMapper.insert(task);

        dispatchIndex(doc.getId());
        auditLogService.log("UPLOAD_DOC", "DOCUMENT", doc.getId(), doc.getTitle());
        return doc;
    }

    @Transactional
    public KbDocument saveTextDocument(Long kbId, String title, String content) throws IOException {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        if (content == null || content.isBlank()) {
            throw new BusinessException("文档内容不能为空");
        }
        String safeTitle = title != null && !title.isBlank() ? title : "生成文档";
        StoredObject stored = storageProvider.storeText(kbId, safeTitle + ".md", content);

        KbDocument doc = new KbDocument();
        doc.setKbId(kbId);
        doc.setTitle(safeTitle);
        doc.setFileName(safeTitle + ".md");
        doc.setFileType(stored.extension());
        doc.setFileSize(stored.size());
        doc.setFilePath(stored.absolutePath());
        doc.setIndexStatus("PENDING");
        doc.setChunkCount(0);
        doc.setEnabled(1);
        doc.setCreatedBy(UserContext.currentUserId());
        documentMapper.insert(doc);

        IndexTask task = new IndexTask();
        task.setDocumentId(doc.getId());
        task.setStatus("PENDING");
        task.setRetryCount(0);
        indexTaskMapper.insert(task);
        dispatchIndex(doc.getId());
        return doc;
    }

    @Transactional
    public void delete(Long kbId, Long docId) {
        KbDocument doc = requireDocument(kbId, docId);
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        vectorIndexService.deleteByDocument(doc.getKbId(), docId);
        documentChunkMapper.deleteByDocumentId(docId);
        try {
            storageProvider.delete(doc.getFilePath());
        } catch (IOException e) {
            log.warn("Failed to delete stored file: {}", doc.getFilePath(), e);
        }
        documentMapper.deleteById(docId);
        searchCacheService.invalidateForKb(kbId);
        auditLogService.log("DELETE_DOC", "DOCUMENT", docId, doc.getTitle());
    }

    public void reindex(Long kbId, Long docId) {
        KbDocument doc = requireDocument(kbId, docId);
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        doc.setIndexStatus("PENDING");
        documentMapper.updateById(doc);
        IndexTask task = new IndexTask();
        task.setDocumentId(docId);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        indexTaskMapper.insert(task);
        dispatchIndex(docId);
    }

    /**
     * Dispatch indexing: use Redis Pub/Sub if enabled, otherwise call directly.
     */
    private void dispatchIndex(Long documentId) {
        if (indexEventPublisher.isEnabled()) {
            indexEventPublisher.publish(documentId);
        } else {
            documentIngestService.scheduleIndex(documentId);
        }
    }

    private String stripExtension(String name) {
        if (name == null || !name.contains(".")) {
            return name;
        }
        return name.substring(0, name.lastIndexOf('.'));
    }
}
