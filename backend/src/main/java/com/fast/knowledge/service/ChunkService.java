package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ChunkService {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+.+$");

    private final KnowledgeProperties properties;
    private final Encoding encoding = Encodings.newDefaultEncodingRegistry().getEncodingForModel("gpt-4o").orElseThrow();

    public ChunkService(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public List<String> split(String text) {
        int chunkSize = properties.getChunk().getSize();
        int overlap = properties.getChunk().getOverlap();
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").trim();
        List<String> paragraphs = splitParagraphs(normalized);
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                continue;
            }
            if (countTokens(paragraph) > chunkSize) {
                flushBuffer(buffer, chunks, chunkSize, overlap);
                chunks.addAll(slidingWindow(paragraph, chunkSize, overlap));
                continue;
            }
            if (buffer.length() > 0 && countTokens(buffer + "\n\n" + paragraph) > chunkSize) {
                flushBuffer(buffer, chunks, chunkSize, overlap);
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(paragraph);
        }
        flushBuffer(buffer, chunks, chunkSize, overlap);
        return chunks;
    }

    public int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    private List<String> splitParagraphs(String text) {
        List<String> parts = new ArrayList<>();
        for (String block : PARAGRAPH_SPLIT.split(text)) {
            if (MARKDOWN_HEADING.matcher(block).find() && block.length() > properties.getChunk().getSize()) {
                String[] lines = block.split("\n");
                StringBuilder section = new StringBuilder();
                for (String line : lines) {
                    if (MARKDOWN_HEADING.matcher(line).matches() && section.length() > 0) {
                        parts.add(section.toString().trim());
                        section = new StringBuilder();
                    }
                    if (section.length() > 0) {
                        section.append("\n");
                    }
                    section.append(line);
                }
                if (section.length() > 0) {
                    parts.add(section.toString().trim());
                }
            } else if (!block.isBlank()) {
                parts.add(block.trim());
            }
        }
        if (parts.isEmpty()) {
            parts.add(text);
        }
        return parts;
    }

    private void flushBuffer(StringBuilder buffer, List<String> chunks, int chunkSize, int overlap) {
        if (buffer.length() == 0) {
            return;
        }
        String content = buffer.toString().trim();
        if (countTokens(content) > chunkSize) {
            chunks.addAll(slidingWindow(content, chunkSize, overlap));
        } else {
            chunks.add(content);
        }
        buffer.setLength(0);
    }

    private List<String> slidingWindow(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String normalized = text.replaceAll("\\s+", " ").trim();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            while (end > start && countTokens(normalized.substring(start, end)) > chunkSize) {
                end--;
            }
            if (end <= start) {
                end = Math.min(start + chunkSize, normalized.length());
            }
            chunks.add(normalized.substring(start, end).trim());
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
