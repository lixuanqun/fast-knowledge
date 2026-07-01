package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.storage.StoredObject;
import com.fast.knowledge.storage.StorageProvider;
import com.fast.knowledge.vector.VectorStore;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.IndexTaskMapper;
import com.fast.knowledge.model.entity.IndexTask;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.vo.DocumentChunkVO;
import com.fast.knowledge.model.vo.DocumentPreviewVO;
import com.fast.knowledge.security.UserContext;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final int PREVIEW_MAX_CHARS = 200_000;

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentIngestService documentIngestService;
    private final VectorStore vectorStore;
    private final StorageProvider storageProvider;
    private final Tika tika = new Tika();

    public DocumentService(DocumentMapper documentMapper,
                           DocumentChunkMapper documentChunkMapper,
                           IndexTaskMapper indexTaskMapper,
                           KnowledgeBaseService knowledgeBaseService,
                           DocumentIngestService documentIngestService,
                           VectorStore vectorStore,
                           StorageProvider storageProvider) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentIngestService = documentIngestService;
        this.vectorStore = vectorStore;
        this.storageProvider = storageProvider;
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

    public DocumentPreviewVO preview(Long kbId, Long docId) throws Exception {
        KbDocument doc = requireDocument(kbId, docId);
        knowledgeBaseService.getById(kbId);

        DocumentPreviewVO vo = new DocumentPreviewVO();
        vo.setDocumentId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setFileType(doc.getFileType());

        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        String content;
        if ("txt".equals(fileType) || "md".equals(fileType)) {
            content = Files.readString(storageProvider.readablePath(doc.getFilePath()));
            vo.setPreviewMode("raw");
        } else {
            content = tika.parseToString(storageProvider.readablePath(doc.getFilePath()).toFile());
            vo.setPreviewMode("extracted");
        }

        vo.setContentLength(content.length());
        if (content.length() > PREVIEW_MAX_CHARS) {
            vo.setContent(content.substring(0, PREVIEW_MAX_CHARS));
            vo.setTruncated(true);
        } else {
            vo.setContent(content);
            vo.setTruncated(false);
        }
        return vo;
    }

    public List<DocumentChunkVO> listChunks(Long kbId, Long docId) {
        requireDocument(kbId, docId);
        knowledgeBaseService.getById(kbId);
        return documentChunkMapper.findByDocumentId(docId).stream()
                .map(this::toChunkVO)
                .collect(Collectors.toList());
    }

    private KbDocument requireDocument(Long kbId, Long docId) {
        KbDocument doc = documentMapper.findById(docId);
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
        return vo;
    }

    @Transactional
    public KbDocument upload(Long kbId, MultipartFile file) throws IOException {
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
        documentMapper.insert(doc);

        IndexTask task = new IndexTask();
        task.setDocumentId(doc.getId());
        task.setStatus("PENDING");
        task.setRetryCount(0);
        indexTaskMapper.insert(task);

        documentIngestService.scheduleIndex(doc.getId());
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
        documentIngestService.scheduleIndex(doc.getId());
        return doc;
    }

    @Transactional
    public void delete(Long docId) {
        KbDocument doc = documentMapper.findById(docId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKbId());
        knowledgeBaseService.checkWritePermission(kb);
        try {
            vectorStore.deleteByDocument(doc.getKbId(), docId);
        } catch (java.io.IOException e) {
            throw new BusinessException("删除索引失败: " + e.getMessage());
        }
        documentChunkMapper.deleteByDocumentId(docId);
        try {
            storageProvider.delete(doc.getFilePath());
        } catch (IOException ignored) {
        }
        documentMapper.deleteById(docId);
    }

    public void reindex(Long docId) {
        KbDocument doc = documentMapper.findById(docId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKbId());
        knowledgeBaseService.checkWritePermission(kb);
        doc.setIndexStatus("PENDING");
        documentMapper.update(doc);
        IndexTask task = new IndexTask();
        task.setDocumentId(docId);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        indexTaskMapper.insert(task);
        documentIngestService.scheduleIndex(docId);
    }

    private String stripExtension(String name) {
        if (name == null || !name.contains(".")) {
            return name;
        }
        return name.substring(0, name.lastIndexOf('.'));
    }
}
