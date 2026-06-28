package com.iot.platform.knowledge.service;

import com.iot.platform.knowledge.service.impl.KnowledgeServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeServiceImplTest {
    @Test
    void pageShouldHandleStringParams() {
        KnowledgeServiceImpl service = new KnowledgeServiceImpl();
        Map<String, Object> params = new HashMap<>();
        params.put("current", "1");
        params.put("size", "10");
        Map<String, Object> result = service.page(params);
        assertThat(result).isNotNull();
        assertThat(result.get("total")).isEqualTo(0);
    }
}
