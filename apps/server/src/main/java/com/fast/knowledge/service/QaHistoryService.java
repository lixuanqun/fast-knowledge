package com.fast.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.mapper.QaHistoryMapper;
import com.fast.knowledge.model.entity.QaHistory;
import com.fast.knowledge.model.vo.QaHistoryVO;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fast.knowledge.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class QaHistoryService {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final QaHistoryMapper qaHistoryMapper;
    private final ObjectMapper objectMapper;

    public QaHistoryService(QaHistoryMapper qaHistoryMapper, ObjectMapper objectMapper) {
        this.qaHistoryMapper = qaHistoryMapper;
        this.objectMapper = objectMapper;
    }

    public void record(Long kbId, String question, String answer, List<SearchHitVO> sources) {
        try {
            QaHistory row = new QaHistory();
            row.setUserId(UserContext.currentUserId());
            row.setKbId(kbId);
            row.setQuestion(question != null ? question : "");
            row.setAnswer(answer != null ? answer : "");
            row.setSourceCount(sources != null ? sources.size() : 0);
            row.setSources(writeSources(sources));
            qaHistoryMapper.insert(row);
        } catch (Exception e) {
            // 运营落库失败不影响问答主路径
            log.warn("Failed to persist QA history: {}", e.getMessage());
        }
    }

    public IPage<QaHistoryVO> page(int pageNum, int pageSize, Long kbId, Long userId) {
        IPage<QaHistory> page = qaHistoryMapper.findPage(new Page<>(pageNum, pageSize), kbId, userId);
        return page.convert(this::toVo);
    }

    public long countAll() {
        return qaHistoryMapper.selectCount(null);
    }

    public String exportCsv(Long kbId, Long userId, int maxRows) {
        IPage<QaHistory> result = qaHistoryMapper.findPage(new Page<>(1, maxRows), kbId, userId);
        StringBuilder sb = new StringBuilder();
        sb.append("id,user_id,kb_id,question,answer,source_count,sources,created_at\n");
        for (QaHistory row : result.getRecords()) {
            sb.append(row.getId()).append(',');
            sb.append(row.getUserId() != null ? row.getUserId() : "").append(',');
            sb.append(row.getKbId()).append(',');
            sb.append(csvEscape(row.getQuestion())).append(',');
            sb.append(csvEscape(row.getAnswer())).append(',');
            sb.append(row.getSourceCount() != null ? row.getSourceCount() : 0).append(',');
            sb.append(csvEscape(summarizeSources(row.getSources()))).append(',');
            sb.append(row.getCreatedAt() != null ? CSV_TIME.format(row.getCreatedAt()) : "").append('\n');
        }
        return sb.toString();
    }

    private String writeSources(List<SearchHitVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String summarizeSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return "";
        }
        try {
            SearchHitVO[] hits = objectMapper.readValue(sourcesJson, SearchHitVO[].class);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hits.length; i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                SearchHitVO h = hits[i];
                sb.append(h.getDocumentTitle() != null ? h.getDocumentTitle() : "")
                        .append("#")
                        .append(h.getDocumentId() != null ? h.getDocumentId() : "");
            }
            return sb.toString();
        } catch (Exception e) {
            return sourcesJson.length() > 200 ? sourcesJson.substring(0, 200) : sourcesJson;
        }
    }

    private QaHistoryVO toVo(QaHistory row) {
        QaHistoryVO vo = new QaHistoryVO();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setKbId(row.getKbId());
        vo.setQuestion(row.getQuestion());
        vo.setAnswer(row.getAnswer());
        vo.setSources(row.getSources());
        vo.setSourceCount(row.getSourceCount());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
