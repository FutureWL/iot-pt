package com.iot.platform.knowledge.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库文档创建/更新 DTO
 * 与前端 KnowledgeDetailVO 字段一致(除 id/version/author/updatedAt 等服务端字段)
 */
@Data
public class KnowledgeDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String category;
    private String title;
    private String summary;
    private String content;
    private String tags;
    private String status;
}