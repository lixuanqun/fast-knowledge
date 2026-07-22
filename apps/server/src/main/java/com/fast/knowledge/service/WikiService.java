package com.fast.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.WikiPageMapper;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.entity.WikiPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WikiService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String INDEX_SLUG = "index";

    private final WikiPageMapper wikiPageMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditLogService auditLogService;

    public WikiService(WikiPageMapper wikiPageMapper,
                       KnowledgeBaseService knowledgeBaseService,
                       AuditLogService auditLogService) {
        this.wikiPageMapper = wikiPageMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditLogService = auditLogService;
    }

    public List<WikiPage> list(Long kbId, String status) {
        knowledgeBaseService.getById(kbId);
        return wikiPageMapper.selectList(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getKbId, kbId)
                .eq(status != null && !status.isBlank(), WikiPage::getStatus, status)
                .orderByDesc(WikiPage::getUpdatedAt));
    }

    public WikiPage getBySlug(Long kbId, String slug) {
        knowledgeBaseService.getById(kbId);
        WikiPage page = wikiPageMapper.findByKbAndSlug(kbId, slug);
        if (page == null) {
            throw new BusinessException("Wiki 页面不存在");
        }
        return page;
    }

    @Transactional
    public WikiPage publish(Long kbId, Long pageId) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        WikiPage page = requirePage(kbId, pageId);
        if (INDEX_SLUG.equals(page.getSlug())) {
            throw new BusinessException("目录页由系统维护，请发布其它文档页");
        }
        page.setStatus(STATUS_PUBLISHED);
        wikiPageMapper.updateById(page);
        rebuildIndex(kbId);
        auditLogService.log(AuditActions.WIKI_PUBLISH, "WIKI", page.getId(),
                "slug=" + page.getSlug() + ", title=" + page.getTitle());
        return wikiPageMapper.selectById(pageId);
    }

    @Transactional
    public WikiPage reject(Long kbId, Long pageId) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        WikiPage page = requirePage(kbId, pageId);
        if (INDEX_SLUG.equals(page.getSlug())) {
            throw new BusinessException("不能驳回目录页");
        }
        page.setStatus(STATUS_DRAFT);
        wikiPageMapper.updateById(page);
        rebuildIndex(kbId);
        auditLogService.log(AuditActions.WIKI_REJECT, "WIKI", page.getId(),
                "slug=" + page.getSlug() + ", title=" + page.getTitle());
        return wikiPageMapper.selectById(pageId);
    }

    /** 根据已发布页重建 slug=index 的知识库目录。 */
    @Transactional
    public WikiPage rebuildIndex(Long kbId) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkWritePermission(kb);
        List<WikiPage> published = wikiPageMapper.selectList(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getKbId, kbId)
                .eq(WikiPage::getStatus, STATUS_PUBLISHED)
                .ne(WikiPage::getSlug, INDEX_SLUG)
                .orderByAsc(WikiPage::getTitle));

        StringBuilder md = new StringBuilder();
        md.append("# 知识库目录\n\n");
        if (published.isEmpty()) {
            md.append("_暂无已发布页面。_\n");
        } else {
            md.append("| 标题 | 链接 |\n| --- | --- |\n");
            for (WikiPage p : published) {
                md.append("| ").append(escapeCell(p.getTitle()))
                        .append(" | `").append(p.getSlug()).append("` |\n");
            }
            md.append("\n## 页面摘要\n\n");
            for (WikiPage p : published) {
                md.append("### ").append(p.getTitle()).append("\n\n");
                md.append("- slug: `").append(p.getSlug()).append("`\n");
                md.append("- 摘要: ").append(firstLine(p.getContentMd())).append("\n\n");
            }
        }

        WikiPage index = wikiPageMapper.findByKbAndSlug(kbId, INDEX_SLUG);
        if (index == null) {
            index = new WikiPage();
            index.setKbId(kbId);
            index.setSlug(INDEX_SLUG);
            index.setTitle("知识库目录");
            index.setContentMd(md.toString());
            index.setStatus(STATUS_PUBLISHED);
            index.setVersion(1);
            index.setSourceDocIds("[]");
            wikiPageMapper.insert(index);
        } else {
            index.setTitle("知识库目录");
            index.setContentMd(md.toString());
            index.setStatus(STATUS_PUBLISHED);
            index.setVersion(index.getVersion() != null ? index.getVersion() + 1 : 1);
            wikiPageMapper.updateById(index);
        }
        return index;
    }

    public List<WikiPage> listPublished(Long kbId) {
        return wikiPageMapper.selectList(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getKbId, kbId)
                .eq(WikiPage::getStatus, STATUS_PUBLISHED)
                .orderByAsc(WikiPage::getTitle));
    }

    private WikiPage requirePage(Long kbId, Long pageId) {
        WikiPage page = wikiPageMapper.selectById(pageId);
        if (page == null || !kbId.equals(page.getKbId())) {
            throw new BusinessException("Wiki 页面不存在");
        }
        return page;
    }

    private static String firstLine(String md) {
        if (md == null || md.isBlank()) {
            return "";
        }
        String line = md.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .findFirst()
                .orElse("");
        return line.length() > 120 ? line.substring(0, 120) + "…" : line;
    }

    private static String escapeCell(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "\\|");
    }
}
