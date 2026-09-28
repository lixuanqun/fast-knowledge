package com.fast.knowledge.graph;

import com.fast.knowledge.model.entity.KgEdge;
import com.fast.knowledge.model.entity.KgEntity;

import java.util.List;

/**
 * WP7 知识图谱存储端口 — 图是可重建的派生索引，本端口只暴露构建与查询两类操作。
 * 实现方：
 * <ul>
 *   <li>{@link FileGraphStore}（默认）：内存图视图 + per-KB JSON 落盘（LightRAG 默认模式同型）；</li>
 *   <li>{@link MysqlGraphStore}：MySQL 邻接表（kg_entity/kg_edge，SQL 可查可运维）。</li>
 * </ul>
 * 升级路径（TuGraph 单机）见 docs/architecture/graph-storage-selection.md。
 */
public interface GraphStore {

    /** 按名称查实体（构建期幂等合并用） */
    KgEntity findEntity(Long kbId, String name);

    /** 按实体 ID 查（日志/运维展示用） */
    KgEntity entityById(Long id);

    /** 插入实体，ID 由实现分配并回填（createdAt 由实现填充） */
    void insertEntity(KgEntity entity);

    /** 边是否已存在（kb + src + dst + relation 唯一） */
    boolean edgeExists(Long kbId, Long srcId, Long dstId, String relation);

    /** 插入边，ID 由实现分配并回填（createdAt 由实现填充） */
    void insertEdge(KgEdge edge);

    /** 删除以指定文档为证据的边（文档删除联动） */
    void deleteEdgesByDocument(Long kbId, Long docId);

    /** 清空知识库全部图数据（重建前调用） */
    void deleteByKb(Long kbId);

    /** 全量实体（实体链接用，插入序） */
    List<KgEntity> entities(Long kbId);

    /** 全量边（PPR 邻接表构建用，插入序） */
    List<KgEdge> edges(Long kbId);
}
