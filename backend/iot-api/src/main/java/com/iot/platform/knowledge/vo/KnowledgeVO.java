package com.iot.platform.knowledge.vo;
import lombok.Data; import java.io.Serializable;
@Data public class KnowledgeVO implements Serializable {
    private Long id; private String category, title, summary, tags;
    private Integer version; private String status, author, updatedAt;
}
