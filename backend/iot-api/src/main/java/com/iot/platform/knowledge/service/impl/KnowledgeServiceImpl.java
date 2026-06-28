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
        Integer current = safeInt(params.get("current"), 1);
        Integer size = safeInt(params.get("size"), 10);
        map.put("current", current); map.put("size", size);
        return map;
    }

    private static Integer safeInt(Object v, Integer defaultVal) {
        if (v == null) return defaultVal;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); }
            catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

}
