package com.fast.knowledge.graph;

import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.model.entity.KgEdge;
import com.fast.knowledge.model.entity.KgEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileGraphStoreTest {

    @TempDir
    Path tempDir;

    private FileGraphStore store;

    @BeforeEach
    void setUp() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getKg().setStorageDir(tempDir.toString());
        store = new FileGraphStore(properties);
    }

    private KgEntity entity(Long kbId, String name) {
        KgEntity e = new KgEntity();
        e.setKbId(kbId);
        e.setName(name);
        e.setType("制度");
        e.setDescription(name + "的描述");
        return e;
    }

    private KgEdge edge(Long kbId, Long src, Long dst, String relation, Long docId) {
        KgEdge edge = new KgEdge();
        edge.setKbId(kbId);
        edge.setSrcId(src);
        edge.setDstId(dst);
        edge.setRelation(relation);
        edge.setEvidenceDocId(docId);
        return edge;
    }

    @Test
    void insertAssignsIdsAndDedupes() {
        store.insertEntity(entity(1L, "公务用车管理办法"));
        store.insertEntity(entity(1L, "市机关事务管理局"));

        KgEntity found = store.findEntity(1L, "公务用车管理办法");
        assertNotNull(found);
        assertEquals(1L, found.getId());
        assertNotNull(found.getCreatedAt());

        // 同名直接 insert 会覆盖 map 项（构建流程经 findEntity 幂等，不会走到此路径）；id 序列继续递增
        store.insertEntity(entity(1L, "公务用车管理办法"));
        assertEquals(3L, store.findEntity(1L, "公务用车管理办法").getId());

        store.insertEdge(edge(1L, 2L, 1L, "发文部门", 100L));
        assertTrue(store.edgeExists(1L, 2L, 1L, "发文部门"));
        assertFalse(store.edgeExists(1L, 2L, 1L, "规定了"));
        assertFalse(store.edgeExists(2L, 2L, 1L, "发文部门"));

        assertEquals(2, store.entities(1L).size());
        assertEquals(1, store.edges(1L).size());
    }

    @Test
    void deleteEdgesByDocumentOnlyRemovesMatchingEvidence() {
        store.insertEntity(entity(1L, "甲"));
        store.insertEntity(entity(1L, "乙"));
        KgEntity jia = store.findEntity(1L, "甲");
        KgEntity yi = store.findEntity(1L, "乙");
        store.insertEdge(edge(1L, jia.getId(), yi.getId(), "相关", 10L));
        store.insertEdge(edge(1L, yi.getId(), jia.getId(), "规定了", 11L));

        store.deleteEdgesByDocument(1L, 10L);

        assertEquals(1, store.edges(1L).size());
        assertTrue(store.edgeExists(1L, yi.getId(), jia.getId(), "规定了"));
    }

    @Test
    void deleteByKbClearsEverythingAndRemovesFile() {
        store.insertEntity(entity(3L, "实体A"));
        store.insertEdge(edge(3L, 1L, 2L, "相关", 5L));
        store.flushAll();

        store.deleteByKb(3L);

        assertEquals(0, store.entities(3L).size());
        assertEquals(0, store.edges(3L).size());
        assertFalse(java.nio.file.Files.exists(tempDir.resolve("kb-3.json")));
    }

    @Test
    void persistsAcrossInstances() {
        store.insertEntity(entity(7L, "车辆维修"));
        KgEntity entity = store.findEntity(7L, "车辆维修");
        store.insertEdge(edge(7L, entity.getId(), 99L, "隶属于", 20L));
        store.flushAll();

        // 模拟应用重启：新实例从文件加载
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getKg().setStorageDir(tempDir.toString());
        FileGraphStore reopened = new FileGraphStore(properties);

        List<KgEntity> entities = reopened.entities(7L);
        assertEquals(1, entities.size());
        assertEquals("车辆维修", entities.get(0).getName());
        assertEquals(entity.getId(), entities.get(0).getId());
        assertNotNull(entities.get(0).getCreatedAt());
        assertEquals(1, reopened.edges(7L).size());
        assertTrue(reopened.edgeExists(7L, entity.getId(), 99L, "隶属于"));
        assertNull(reopened.findEntity(7L, "不存在的实体"));
    }

    @Test
    void kbIsolationBetweenGraphs() {
        store.insertEntity(entity(1L, "甲库实体"));
        store.insertEntity(entity(2L, "乙库实体"));

        assertEquals(1, store.entities(1L).size());
        assertEquals(1, store.entities(2L).size());
        assertNull(store.findEntity(1L, "乙库实体"));
    }
}
