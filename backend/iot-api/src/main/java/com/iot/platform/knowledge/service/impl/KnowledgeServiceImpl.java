package com.iot.platform.knowledge.service.impl;
import com.iot.platform.knowledge.service.KnowledgeService;
import com.iot.platform.knowledge.vo.KnowledgeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashMap; import java.util.Map;
// 注:知识库需新建 knowledge_base 表(后续 sprint),目前返回空。
@Service @RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {
    @Override public Map<String, Object> page(Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("records", java.util.Collections.emptyList());
        map.put("total", 0);
        Integer current = (Integer) params.getOrDefault("current", 1);
        Integer size = (Integer) params.getOrDefault("size", 10);
        map.put("current", current); map.put("size", size);
        return map;
    }
}
