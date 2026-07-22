package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.entity.WikiPage;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 薄双路路由：章节/制度/目录类问法优先命中已发布 Wiki；否则交由调用方走 HYBRID。
 */
@Service
public class WikiQueryRouter {

    private static final Pattern NAV_PATTERN = Pattern.compile(
            "(目录|索引|章节|第.+章|第.+节|制度|办法|规定|规程|手册|导航|大纲|清单|一览)",
            Pattern.CASE_INSENSITIVE);

    private final KnowledgeProperties properties;
    private final WikiService wikiService;

    public WikiQueryRouter(KnowledgeProperties properties, WikiService wikiService) {
        this.properties = properties;
        this.wikiService = wikiService;
    }

    public boolean isEnabled() {
        return properties.getWiki().isEnabled() && properties.getWiki().isQueryRouting();
    }

    public boolean looksLikeWikiQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return NAV_PATTERN.matcher(query.trim()).find();
    }

    /**
     * @return 命中的 Wiki 转成的检索 hits；空表示应回落 HYBRID
     */
    public List<SearchHitVO> resolveWikiHits(Long kbId, String query) {
        if (!isEnabled() || !looksLikeWikiQuery(query)) {
            return List.of();
        }
        List<WikiPage> published = wikiService.listPublished(kbId);
        if (published.isEmpty()) {
            return List.of();
        }

        String q = query.toLowerCase(Locale.ROOT);
        List<WikiPage> matched = new ArrayList<>();

        // 目录类问法优先 index
        if (q.contains("目录") || q.contains("索引") || q.contains("导航") || q.contains("大纲") || q.contains("一览")) {
            published.stream()
                    .filter(p -> WikiService.INDEX_SLUG.equals(p.getSlug()))
                    .findFirst()
                    .ifPresent(matched::add);
        }

        for (WikiPage page : published) {
            if (WikiService.INDEX_SLUG.equals(page.getSlug()) && matched.stream().anyMatch(p -> p.getId().equals(page.getId()))) {
                continue;
            }
            String title = page.getTitle() != null ? page.getTitle().toLowerCase(Locale.ROOT) : "";
            String slug = page.getSlug() != null ? page.getSlug().toLowerCase(Locale.ROOT) : "";
            if ((!title.isEmpty() && q.contains(title)) || (!slug.isEmpty() && q.contains(slug))) {
                matched.add(page);
            }
        }

        // 无精确标题命中但确认为 Wiki 问法：至少返回 index（若有）或前几页
        if (matched.isEmpty()) {
            published.stream()
                    .filter(p -> WikiService.INDEX_SLUG.equals(p.getSlug()))
                    .findFirst()
                    .ifPresentOrElse(matched::add, () -> matched.addAll(published.stream().limit(3).toList()));
        }

        return matched.stream().limit(5).map(this::toHit).toList();
    }

    private SearchHitVO toHit(WikiPage page) {
        SearchHitVO hit = new SearchHitVO();
        hit.setDocumentId(page.getId());
        hit.setChunkId(page.getId());
        hit.setDocumentTitle("[Wiki] " + page.getTitle());
        hit.setSection(page.getSlug());
        hit.setDocType("WIKI");
        hit.setContent(truncate(page.getContentMd(), 6000));
        hit.setScore(1.0);
        return hit;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
