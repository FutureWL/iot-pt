package com.iot.platform.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.knowledge.dto.KnowledgeDTO;
import com.iot.platform.knowledge.dto.KnowledgeQuery;
import com.iot.platform.knowledge.entity.KnowledgeBase;
import com.iot.platform.knowledge.mapper.KnowledgeBaseMapper;
import com.iot.platform.knowledge.service.KnowledgeService;
import com.iot.platform.knowledge.vo.KnowledgeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeBaseMapper mapper;

    @Override
    public Map<String, Object> page(KnowledgeQuery query) {
        Page<KnowledgeBase> pageReq = new Page<>(
                query.getCurrent() == null ? 1 : query.getCurrent(),
                query.getSize() == null ? 10 : query.getSize()
        );
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (query.getCategory() != null && !query.getCategory().isBlank()) {
            wrapper.eq(KnowledgeBase::getCategory, query.getCategory());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(KnowledgeBase::getStatus, query.getStatus());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(KnowledgeBase::getTitle, kw)
                    .or().like(KnowledgeBase::getTags, kw));
        }
        wrapper.orderByDesc(KnowledgeBase::getUpdatedAt);

        Page<KnowledgeBase> result = mapper.selectPage(pageReq, wrapper);

        Map<String, Object> resp = new HashMap<>();
        resp.put("records", result.getRecords().stream().map(this::toVO).toList());
        resp.put("total", result.getTotal());
        resp.put("size", result.getSize());
        resp.put("current", result.getCurrent());
        resp.put("pages", result.getPages());
        return resp;
    }

    @Override
    public KnowledgeVO detail(Long id) {
        KnowledgeBase e = mapper.selectById(id);
        if (e == null) return null;
        return toVO(e);
    }

    @Override
    public Long create(KnowledgeDTO dto) {
        KnowledgeBase e = new KnowledgeBase();
        BeanUtils.copyProperties(dto, e, "id");
        e.setId(null);
        if (e.getVersion() == null) e.setVersion(1);
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("DRAFT");
        e.setAuthor(currentUsername());
        e.setTenantId(1L);
        mapper.insert(e);
        return e.getId();
    }

    @Override
    public void update(KnowledgeDTO dto) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        KnowledgeBase exist = mapper.selectById(dto.getId());
        if (exist == null) {
            throw new IllegalArgumentException("文档不存在: id=" + dto.getId());
        }
        // 仅允许覆盖业务字段,作者/创建时间保持
        exist.setCategory(dto.getCategory());
        exist.setTitle(dto.getTitle());
        exist.setSummary(dto.getSummary());
        exist.setContent(dto.getContent());
        exist.setTags(dto.getTags());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            exist.setStatus(dto.getStatus());
        }
        exist.setVersion(exist.getVersion() == null ? 1 : exist.getVersion() + 1);
        mapper.updateById(exist);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private KnowledgeVO toVO(KnowledgeBase e) {
        KnowledgeVO v = new KnowledgeVO();
        BeanUtils.copyProperties(e, v);
        // LocalDateTime 由全局 jackson (date-format=yyyy-MM-dd HH:mm:ss) 序列化,无需手动转字符串
        return v;
    }

    private String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }
}