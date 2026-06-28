package com.iot.platform.knowledge.controller;

import com.iot.platform.common.R;
import com.iot.platform.knowledge.dto.KnowledgeDTO;
import com.iot.platform.knowledge.dto.KnowledgeQuery;
import com.iot.platform.knowledge.service.KnowledgeService;
import com.iot.platform.knowledge.vo.KnowledgeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService service;

    /**
     * 分页查询(支持 keyword / category / status 过滤)
     *
     * 注意:前端 CrudList 用 pageNum/pageSize,后端 Page 组件用 current/size,
     *      这里做一次归一化,避免参数名不一致导致分页失效。
     */
    @GetMapping("/page")
    public R<Map<String, Object>> page(@RequestParam Map<String, Object> params) {
        KnowledgeQuery query = new KnowledgeQuery();
        Object pageNum = params.get("pageNum");
        Object pageSize = params.get("pageSize");
        if (pageNum != null) query.setCurrent(toInt(pageNum, 1));
        if (pageSize != null) query.setSize(toInt(pageSize, 10));
        Object kw = params.get("keyword");
        if (kw != null) query.setKeyword(kw.toString());
        Object cat = params.get("category");
        if (cat != null) query.setCategory(cat.toString());
        Object st = params.get("status");
        if (st != null) query.setStatus(st.toString());
        return R.ok(service.page(query));
    }

    /**
     * 详情(包含正文 content)
     */
    @GetMapping("/{id}")
    public R<KnowledgeVO> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    /**
     * 创建 — 响应 data 为 {id} 与前端 createKnowledge 类型契约一致
     */
    @PostMapping
    public R<Map<String, Long>> create(@RequestBody KnowledgeDTO dto) {
        Long id = service.create(dto);
        return R.ok(Map.of("id", id));
    }

    /**
     * 更新
     */
    @PutMapping
    public R<Void> update(@RequestBody KnowledgeDTO dto) {
        service.update(dto);
        return R.ok();
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    private static Integer toInt(Object v, Integer def) {
        if (v == null) return def;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); }
        catch (NumberFormatException e) { return def; }
    }
}