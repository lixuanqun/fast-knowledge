package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fast.knowledge.model.entity.LlmTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface LlmTraceMapper extends BaseMapper<LlmTrace> {

    @Select("SELECT scene, COUNT(*) AS calls, SUM(latency_ms) AS total_ms, " +
            "AVG(latency_ms) AS avg_ms, SUM(prompt_chars) AS prompt_chars, SUM(response_chars) AS response_chars " +
            "FROM kb_llm_trace WHERE created_at >= #{since} " +
            "GROUP BY scene ORDER BY calls DESC")
    List<Map<String, Object>> statsByScene(LocalDateTime since);

    @Select("SELECT DATE(created_at) AS day, COUNT(*) AS calls, SUM(latency_ms) AS total_ms " +
            "FROM kb_llm_trace WHERE created_at >= #{since} " +
            "GROUP BY DATE(created_at) ORDER BY day")
    List<Map<String, Object>> dailyStats(LocalDateTime since);
}
