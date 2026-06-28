package com.iot.platform.knowledge.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class KnowledgeQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Integer current = 1;
    private Integer size = 10;
    private String keyword;
    private String category;
    private String status;
}