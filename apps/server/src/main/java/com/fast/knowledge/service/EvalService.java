package com.fast.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.EvalCaseMapper;
import com.fast.knowledge.mapper.EvalDatasetMapper;
import com.fast.knowledge.mapper.EvalRunItemMapper;
import com.fast.knowledge.mapper.EvalRunMapper;
import com.fast.knowledge.model.dto.EvalCaseRequest;
import com.fast.knowledge.model.entity.EvalCase;
import com.fast.knowledge.model.entity.EvalDataset;
import com.fast.knowledge.model.entity.EvalRun;
import com.fast.knowledge.model.entity.EvalRunItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评测闭环（WP3）：数据集/用例 CRUD + 运行编排。
 * 指标计算在 {@link EvalRunProcessor}（异步执行）。
 */
@Slf4j
@Service
public class EvalService {

    private final EvalDatasetMapper datasetMapper;
    private final EvalCaseMapper caseMapper;
    private final EvalRunMapper runMapper;
    private final EvalRunItemMapper runItemMapper;
    private final EvalRunProcessor runProcessor;
    private final ObjectMapper objectMapper;

    public EvalService(EvalDatasetMapper datasetMapper, EvalCaseMapper caseMapper,
                       EvalRunMapper runMapper, EvalRunItemMapper runItemMapper,
                       EvalRunProcessor runProcessor, ObjectMapper objectMapper) {
        this.datasetMapper = datasetMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.runItemMapper = runItemMapper;
        this.runProcessor = runProcessor;
        this.objectMapper = objectMapper;
    }

    // ---- 数据集 ----

    public List<EvalDataset> listDatasets(Long kbId) {
        return datasetMapper.selectList(Wrappers.<EvalDataset>lambdaQuery()
                .eq(kbId != null, EvalDataset::getKbId, kbId)
                .orderByDesc(EvalDataset::getId));
    }

    public EvalDataset createDataset(EvalDataset dataset) {
        if (dataset.getTopK() == null || dataset.getTopK() < 1 || dataset.getTopK() > 50) {
            dataset.setTopK(8);
        }
        datasetMapper.insert(dataset);
        return dataset;
    }

    public EvalDataset updateDataset(Long id, EvalDataset patch) {
        EvalDataset existing = requireDataset(id);
        if (patch.getName() != null && !patch.getName().isBlank()) {
            existing.setName(patch.getName());
        }
        if (patch.getDescription() != null) {
            existing.setDescription(patch.getDescription());
        }
        if (patch.getTopK() != null && patch.getTopK() >= 1 && patch.getTopK() <= 50) {
            existing.setTopK(patch.getTopK());
        }
        if (patch.getKbId() != null) {
            existing.setKbId(patch.getKbId());
        }
        datasetMapper.updateById(existing);
        return existing;
    }

    public void deleteDataset(Long id) {
        requireDataset(id);
        List<EvalRun> runs = runMapper.selectList(Wrappers.<EvalRun>lambdaQuery()
                .eq(EvalRun::getDatasetId, id));
        for (EvalRun run : runs) {
            runItemMapper.delete(Wrappers.<EvalRunItem>lambdaQuery().eq(EvalRunItem::getRunId, run.getId()));
        }
        runMapper.delete(Wrappers.<EvalRun>lambdaQuery().eq(EvalRun::getDatasetId, id));
        caseMapper.delete(Wrappers.<EvalCase>lambdaQuery().eq(EvalCase::getDatasetId, id));
        datasetMapper.deleteById(id);
    }

    // ---- 用例 ----

    public List<EvalCase> listCases(Long datasetId) {
        return caseMapper.selectList(Wrappers.<EvalCase>lambdaQuery()
                .eq(EvalCase::getDatasetId, datasetId)
                .orderByDesc(EvalCase::getId));
    }

    public EvalCase addCase(Long datasetId, EvalCaseRequest req) {
        requireDataset(datasetId);
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new BusinessException("评测问题不能为空");
        }
        if ((req.getExpectedChunkIds() == null || req.getExpectedChunkIds().isEmpty())
                && (req.getExpectedKeywords() == null || req.getExpectedKeywords().isEmpty())) {
            throw new BusinessException("期望 chunk 与期望关键词至少填一项，否则无法评估");
        }
        EvalCase evalCase = new EvalCase();
        evalCase.setDatasetId(datasetId);
        evalCase.setQuestion(req.getQuestion().trim());
        evalCase.setExpectedChunkIds(toJson(req.getExpectedChunkIds()));
        evalCase.setExpectedKeywords(toJson(req.getExpectedKeywords()));
        evalCase.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        caseMapper.insert(evalCase);
        return evalCase;
    }

    public void updateCase(Long id, EvalCaseRequest req) {
        EvalCase existing = caseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("评测用例不存在");
        }
        if (req.getQuestion() != null && !req.getQuestion().isBlank()) {
            existing.setQuestion(req.getQuestion().trim());
        }
        if (req.getExpectedChunkIds() != null) {
            existing.setExpectedChunkIds(toJson(req.getExpectedChunkIds()));
        }
        if (req.getExpectedKeywords() != null) {
            existing.setExpectedKeywords(toJson(req.getExpectedKeywords()));
        }
        if (req.getEnabled() != null) {
            existing.setEnabled(req.getEnabled());
        }
        caseMapper.updateById(existing);
    }

    public void deleteCase(Long id) {
        caseMapper.deleteById(id);
    }

    // ---- 运行 ----

    public EvalRun startRun(Long datasetId) {
        EvalDataset dataset = requireDataset(datasetId);
        List<EvalCase> cases = caseMapper.selectList(Wrappers.<EvalCase>lambdaQuery()
                .eq(EvalCase::getDatasetId, datasetId)
                .eq(EvalCase::getEnabled, 1));
        if (cases.isEmpty()) {
            throw new BusinessException("评测数据集没有启用的用例，请先添加用例");
        }
        if (runProcessor.isRunning()) {
            throw new BusinessException("已有评测运行进行中，请稍后再试");
        }

        EvalRun run = new EvalRun();
        run.setDatasetId(datasetId);
        run.setKbId(dataset.getKbId());
        run.setTopK(dataset.getTopK());
        run.setStatus("RUNNING");
        run.setTotalCases(cases.size());
        run.setStartedAt(java.time.LocalDateTime.now());
        runMapper.insert(run);

        // UserContext.wrap 传播提交者身份到异步线程（检索权限校验依赖）
        runProcessor.execute(UserContext.wrap(() -> runProcessor.runEvaluation(run.getId(), dataset, cases)), run.getId());
        return run;
    }

    public List<EvalRun> listRuns(Long datasetId) {
        return runMapper.selectList(Wrappers.<EvalRun>lambdaQuery()
                .eq(EvalRun::getDatasetId, datasetId)
                .orderByDesc(EvalRun::getId));
    }

    public EvalRun getRun(Long runId) {
        EvalRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException("评测运行不存在");
        }
        return run;
    }

    public List<EvalRunItem> listRunItems(Long runId) {
        return runItemMapper.selectList(Wrappers.<EvalRunItem>lambdaQuery()
                .eq(EvalRunItem::getRunId, runId)
                .orderByAsc(EvalRunItem::getId));
    }

    private EvalDataset requireDataset(Long id) {
        EvalDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException("评测数据集不存在");
        }
        return dataset;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化评测字段失败: {}", e.getMessage());
            return null;
        }
    }
}
