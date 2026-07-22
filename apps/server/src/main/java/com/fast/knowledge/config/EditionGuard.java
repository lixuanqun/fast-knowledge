package com.fast.knowledge.config;

import com.fast.knowledge.common.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 企业版能力门控（无 License Server，仅配置 knowledge.edition）。
 */
@Component
public class EditionGuard {

    private final KnowledgeProperties properties;

    public EditionGuard(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public void requireEnterprise(String feature) {
        if (!properties.isEnterprise()) {
            throw new BusinessException(403,
                    "「" + feature + "」为企业版功能，请设置 knowledge.edition=enterprise（或启用 enterprise profile）");
        }
    }

    public boolean isEnterprise() {
        return properties.isEnterprise();
    }
}
