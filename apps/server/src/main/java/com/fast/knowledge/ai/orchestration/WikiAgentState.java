package com.fast.knowledge.ai.orchestration;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;

/**
 * Wiki 维护 Agent 图共享状态（langgraph4j AgentState）。
 * 节点间以部分更新传递；lintIssues/iteration 由条件边消费。
 */
public class WikiAgentState extends AgentState {

    public WikiAgentState(Map<String, Object> state) {
        super(state);
    }

    public Long kbId() {
        return value("kbId", (Long) null);
    }

    public Long docId() {
        return value("docId", (Long) null);
    }

    public String title() {
        return value("title", (String) null);
    }

    /** 文号（可能为空，规则 Lint 用） */
    public String docNo() {
        return value("docNo", (String) null);
    }

    /** 文档元数据提示（编译 Prompt 用） */
    public String meta() {
        return value("meta", (String) null);
    }

    public String docText() {
        return value("docText", (String) null);
    }

    /** 既有 Wiki 页内容；空串表示无旧页（走全量编译分支） */
    public String existingMd() {
        return value("existingMd", "");
    }

    public String draftMd() {
        return value("draftMd", "");
    }

    public List<String> lintIssues() {
        List<String> issues = value("lintIssues", (List<String>) null);
        return issues != null ? issues : List.of();
    }

    public int iteration() {
        return value("iteration", 0);
    }
}
