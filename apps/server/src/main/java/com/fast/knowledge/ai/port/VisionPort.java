package com.fast.knowledge.ai.port;

/**
 * 视觉问答端口 — 图片 + 文本提问，返回模型分析结果。
 * 实现方负责多模态消息装配（qwen-vl 系列等视觉模型）。
 */
public interface VisionPort {

    /**
     * @param imageBase64 图片内容（不含 data: 前缀的纯 base64）
     * @param mimeType    图片类型（image/jpeg、image/png、image/webp）
     * @param question    用户问题
     * @return 模型回答
     */
    String askAboutImage(String imageBase64, String mimeType, String question);
}
