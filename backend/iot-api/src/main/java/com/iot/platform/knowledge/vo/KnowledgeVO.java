package com.iot.platform.knowledge.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档 VO(列表/详情通用)
 *
 * 列表场景:content=null,前端不展示正文
 * 详情场景:由 detail 接口单独填充 content
 */
@Data
public class KnowledgeVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String category;
    private String title;
    private String summary;
    /** 仅 detail 返回 */
    private String content;
    private String tags;
    private Integer version;
    private String status;
    private String author;
    private LocalDateTime updatedAt;
}