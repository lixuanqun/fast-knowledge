package com.fast.knowledge.ai.orchestration;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.WikiChangeLogMapper;
import com.fast.knowledge.mapper.WikiPageMapper;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.WikiChangeLog;
import com.fast.knowledge.model.entity.WikiPage;
import com.fast.knowledge.service.AuditLogService;
import com.fast.knowledge.service.MetricsService;
import com.fast.knowledge.service.TextExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikiAgentServiceTest {

    @Mock private ChatPort chatPort;
    @Mock private DocumentMapper documentMapper;
    @Mock private WikiPageMapper wikiPageMapper;
    @Mock private WikiChangeLogMapper wikiChangeLogMapper;
    @Mock private TextExtractionService textExtractionService;
    @Mock private AuditLogService auditLogService;
    @Mock private MetricsService metricsService;

    private KnowledgeProperties properties;
    private WikiAgentService service;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        properties.getWiki().getAgent().setEnabled(true);
        service = new WikiAgentService(chatPort, documentMapper, wikiPageMapper, wikiChangeLogMapper,
                textExtractionService, properties, auditLogService, metricsService,
                new ObjectMapper(), null);
        // 单测环境：self 指向实例自身（跳过 Spring 代理，仅验证逻辑；事务由生产环境代理保证）
        ReflectionTestUtils.setField(service, "self", service);
    }

    private KbDocument doc() {
        KbDocument doc = new KbDocument();
        doc.setId(1L);
        doc.setKbId(10L);
        doc.setTitle("设备维保制度");
        doc.setDocNo("WB-2026-001");
        return doc;
    }

    private String goodDraft() {
        return "# 设备维保制度（文号 WB-2026-001）\n\n- 每季度点检\n- 年度大修\n\n来源文档 ID: 1\n";
    }

    @Test
    void noExistingPageRoutesToCompileAndPersistsChangeLog() throws Exception {
        when(documentMapper.selectById(1L)).thenReturn(doc());
        when(textExtractionService.extractFullText(any())).thenReturn("文档正文内容，包含条款。");
        when(wikiPageMapper.findByKbAndSlug(10L, "doc-1")).thenReturn(null);
        properties.getWiki().getAgent().setLlmLintEnabled(false);
        when(chatPort.complete(anyString(), anyString())).thenReturn(goodDraft());

        service.run(1L);

        // 无旧页 → 编译分支（Prompt 含「Wiki 编译助手」），且只调用一次
        verify(chatPort).complete(contains("Wiki 编译助手"), anyString());
        verify(chatPort, times(1)).complete(anyString(), anyString());

        ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
        verify(wikiPageMapper).insert(pageCaptor.capture());
        assertThat(pageCaptor.getValue().getVersion()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getStatus()).isEqualTo("DRAFT");

        ArgumentCaptor<WikiChangeLog> logCaptor = ArgumentCaptor.forClass(WikiChangeLog.class);
        verify(wikiChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getChangeType()).isEqualTo("COMPILE");
        assertThat(logCaptor.getValue().getFromVersion()).isNull();
        assertThat(logCaptor.getValue().getToVersion()).isEqualTo(1);
        assertThat(logCaptor.getValue().getSummary()).contains("Lint 通过");
        verify(metricsService).countWikiAgent("COMPILE");
    }

    @Test
    void existingPageRoutesToMergeAndKeepsVersionIncrement() throws Exception {
        when(documentMapper.selectById(1L)).thenReturn(doc());
        when(textExtractionService.extractFullText(any())).thenReturn("更新后的正文。");
        WikiPage existing = new WikiPage();
        existing.setId(99L);
        existing.setKbId(10L);
        existing.setSlug("doc-1");
        existing.setContentMd("# 旧页（含人工维护段落）\n\n来源文档 ID: 1\n");
        existing.setVersion(3);
        when(wikiPageMapper.findByKbAndSlug(10L, "doc-1")).thenReturn(existing);
        properties.getWiki().getAgent().setLlmLintEnabled(false);
        // 合并结果保留旧页人工内容 + 新要点
        when(chatPort.complete(anyString(), anyString()))
                .thenReturn("# 设备维保制度\n\n- 人工维护段落（保留）\n- 新增要点\n\n来源文档 ID: 1\n");

        service.run(1L);

        // 合并 Prompt 携带旧页内容
        verify(chatPort).complete(contains("Wiki 维护助手"), contains("人工维护段落"));
        verify(wikiPageMapper).updateById(any(WikiPage.class));

        ArgumentCaptor<WikiChangeLog> logCaptor = ArgumentCaptor.forClass(WikiChangeLog.class);
        verify(wikiChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getChangeType()).isEqualTo("MERGE");
        assertThat(logCaptor.getValue().getFromVersion()).isEqualTo(3);
        assertThat(logCaptor.getValue().getToVersion()).isEqualTo(4);
    }

    @Test
    void lintLoopCapsAtMaxIterationsAndKeepsDraftStatus() throws Exception {
        when(documentMapper.selectById(1L)).thenReturn(doc());
        when(textExtractionService.extractFullText(any())).thenReturn("正文。");
        when(wikiPageMapper.findByKbAndSlug(10L, "doc-1")).thenReturn(null);
        properties.getWiki().getAgent().setLlmLintEnabled(false);
        // 永远缺少来源行 → 规则 Lint 恒不通过
        when(chatPort.complete(anyString(), anyString())).thenReturn("# 标题\n\n内容过短且无来源行");

        service.run(1L);

        // merge 1 次 + revise 2 次（lint→revise 循环封顶 maxLintIterations=2）
        verify(chatPort, times(3)).complete(anyString(), anyString());

        ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
        verify(wikiPageMapper).insert(pageCaptor.capture());
        assertThat(pageCaptor.getValue().getStatus()).isEqualTo("DRAFT");

        ArgumentCaptor<WikiChangeLog> logCaptor = ArgumentCaptor.forClass(WikiChangeLog.class);
        verify(wikiChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSummary()).contains("Lint 未通过");
    }

    @Test
    void autoPublishPublishesPage() throws Exception {
        when(documentMapper.selectById(1L)).thenReturn(doc());
        when(textExtractionService.extractFullText(any())).thenReturn("正文。");
        when(wikiPageMapper.findByKbAndSlug(10L, "doc-1")).thenReturn(null);
        properties.getWiki().setAutoPublish(true);
        properties.getWiki().getAgent().setLlmLintEnabled(false);
        when(chatPort.complete(anyString(), anyString())).thenReturn(goodDraft());

        service.run(1L);

        ArgumentCaptor<WikiPage> pageCaptor = ArgumentCaptor.forClass(WikiPage.class);
        verify(wikiPageMapper, atLeastOnce()).insert(pageCaptor.capture());
        assertThat(pageCaptor.getValue().getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void ruleLintDetectsMissingSourceLineAndDocNo() {
        List<String> issues = WikiAgentService.ruleLint("# 标题\n\n没有来源行也没有文号", "WB-2026-001");
        assertThat(issues).anyMatch(i -> i.contains("来源文档 ID"));
        assertThat(issues).anyMatch(i -> i.contains("WB-2026-001"));

        assertThat(WikiAgentService.ruleLint(goodDraft(), "WB-2026-001")).isEmpty();
    }
}
