package com.fast.knowledge.graph;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.mapper.KgEdgeMapper;
import com.fast.knowledge.mapper.KgEntityMapper;
import com.fast.knowledge.model.entity.KgEdge;
import com.fast.knowledge.model.entity.KgEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 可选图存储 — MySQL 邻接表（kg_entity/kg_edge）。
 * SQL 可查可运维、与事实同库同事务，运维偏好关系库管理的部署可选
 * {@code knowledge.kg.storage=mysql}。默认引擎为 {@link FileGraphStore}。
 */
@Component
@ConditionalOnProperty(name = "knowledge.kg.storage", havingValue = "mysql")
public class MysqlGraphStore implements GraphStore {

    private final KgEntityMapper entityMapper;
    private final KgEdgeMapper edgeMapper;

    public MysqlGraphStore(KgEntityMapper entityMapper, KgEdgeMapper edgeMapper) {
        this.entityMapper = entityMapper;
        this.edgeMapper = edgeMapper;
    }

    @Override
    public KgEntity findEntity(Long kbId, String name) {
        return entityMapper.selectOne(Wrappers.<KgEntity>lambdaQuery()
                .eq(KgEntity::getKbId, kbId)
                .eq(KgEntity::getName, name)
                .last("LIMIT 1"));
    }

    @Override
    public KgEntity entityById(Long id) {
        return entityMapper.selectById(id);
    }

    @Override
    public void insertEntity(KgEntity entity) {
        entityMapper.insert(entity);
    }

    @Override
    public boolean edgeExists(Long kbId, Long srcId, Long dstId, String relation) {
        Long count = edgeMapper.selectCount(Wrappers.<KgEdge>lambdaQuery()
                .eq(KgEdge::getKbId, kbId)
                .eq(KgEdge::getSrcId, srcId)
                .eq(KgEdge::getDstId, dstId)
                .eq(KgEdge::getRelation, relation));
        return count != null && count > 0;
    }

    @Override
    public void insertEdge(KgEdge edge) {
        edgeMapper.insert(edge);
    }

    @Override
    public void deleteEdgesByDocument(Long kbId, Long docId) {
        edgeMapper.delete(Wrappers.<KgEdge>lambdaQuery()
                .eq(KgEdge::getKbId, kbId)
                .eq(KgEdge::getEvidenceDocId, docId));
    }

    @Override
    public void deleteByKb(Long kbId) {
        edgeMapper.delete(Wrappers.<KgEdge>lambdaQuery().eq(KgEdge::getKbId, kbId));
        entityMapper.delete(Wrappers.<KgEntity>lambdaQuery().eq(KgEntity::getKbId, kbId));
    }

    @Override
    public List<KgEntity> entities(Long kbId) {
        return entityMapper.selectList(Wrappers.<KgEntity>lambdaQuery()
                .eq(KgEntity::getKbId, kbId)
                .orderByAsc(KgEntity::getId));
    }

    @Override
    public List<KgEdge> edges(Long kbId) {
        return edgeMapper.selectList(Wrappers.<KgEdge>lambdaQuery()
                .eq(KgEdge::getKbId, kbId)
                .orderByAsc(KgEdge::getId));
    }
}
