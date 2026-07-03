package com.fast.knowledge.langchain4j.ingest;

import com.fast.knowledge.service.ChunkService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 ChunkService 的 Markdown 标题感知分块逻辑适配为 LangChain4j DocumentSplitter。
 */
@Component
public class KbDocumentSplitter implements DocumentSplitter {

    private final ChunkService chunkService;

    public KbDocumentSplitter(ChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @Override
    public List<TextSegment> split(Document document) {
        List<String> parts = chunkService.split(document.text());
        Metadata base = document.metadata() != null
                ? document.metadata().copy()
                : new Metadata();
        List<TextSegment> segments = new ArrayList<>(parts.size());
        for (String part : parts) {
            segments.add(TextSegment.from(part, base.copy()));
        }
        return segments;
    }
}
