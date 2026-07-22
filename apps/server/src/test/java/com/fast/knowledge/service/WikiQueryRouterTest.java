package com.fast.knowledge.service;

import com.fast.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class WikiQueryRouterTest {

    @Mock
    private WikiService wikiService;

    private WikiQueryRouter router;

    @BeforeEach
    void setUp() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getWiki().setEnabled(true);
        properties.getWiki().setQueryRouting(true);
        router = new WikiQueryRouter(properties, wikiService);
    }

    @Test
    void detectsNavQueries() {
        assertTrue(router.looksLikeWikiQuery("安全制度目录"));
        assertTrue(router.looksLikeWikiQuery("第三章 维保规程"));
        assertTrue(router.looksLikeWikiQuery("设备手册导航"));
        assertFalse(router.looksLikeWikiQuery("电机轴承型号是什么"));
    }
}
