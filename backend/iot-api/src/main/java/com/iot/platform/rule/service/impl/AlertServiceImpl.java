package com.iot.platform.rule.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.common.BusinessException;
import com.iot.platform.rule.dto.HandleAlertDTO;
import com.iot.platform.rule.dto.IotAlertQueryDTO;
import com.iot.platform.rule.entity.IotAlert;
import com.iot.platform.rule.entity.IotRule;
import com.iot.platform.rule.mapper.IotAlertMapper;
import com.iot.platform.rule.mapper.IotRuleMapper;
import com.iot.platform.rule.service.AlertService;
import com.iot.platform.rule.vo.IotAlertVO;
import com.iot.platform.tenant.TenantContext;
import com.iot.platform.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final IotAlertMapper alertMapper;
    private final IotRuleMapper ruleMapper;

    @Override
    public IPage<IotAlertVO> page(IotAlertQueryDTO q) {
        Long tenantId = TenantContext.getTenantId();
        Page<IotAlert> page = new Page<>(
                q.getPageNum() == null ? 1 : q.getPageNum(),
                q.getPageSize() == null ? 10 : q.getPageSize()
        );
        LambdaQueryWrapper<IotAlert> w = new LambdaQueryWrapper<IotAlert>()
                .eq(IotAlert::getTenantId, tenantId)
                .eq(q.getRuleId() != null, IotAlert::getRuleId, q.getRuleId())
                .eq(q.getDeviceId() != null, IotAlert::getDeviceId, q.getDeviceId())
                .eq(q.getStatus() != null, IotAlert::getStatus, q.getStatus())
                .eq(q.getLevel() != null && !q.getLevel().isEmpty(), IotAlert::getLevel, q.getLevel())
                .and(q.getKeyword() != null && !q.getKeyword().isEmpty(), ww -> ww
                        .like(IotAlert::getTitle, q.getKeyword())
                        .or().like(IotAlert::getContent, q.getKeyword()))
                .orderByDesc(IotAlert::getCreatedAt);
        IPage<IotAlert> res = alertMapper.selectPage(page, w);
        IPage<IotAlertVO> voPage = res.convert(this::toVO);
        // 关联规则名
        if (!voPage.getRecords().isEmpty()) {
            java.util.Set<Long> ruleIds = new java.util.HashSet<>();
            for (IotAlertVO v : voPage.getRecords()) {
                if (v.getRuleId() != null) ruleIds.add(v.getRuleId());
            }
            if (!ruleIds.isEmpty()) {
                Map<Long, String> nameMap = new HashMap<>();
                for (IotRule r : ruleMapper.selectBatchIds(ruleIds)) {
                    nameMap.put(r.getId(), r.getRuleName());
                }
                voPage.getRecords().forEach(v -> v.setRuleName(nameMap.get(v.getRuleId())));
            }
        }
        return voPage;
    }

    @Override
    public IotAlertVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();
        IotAlert a = alertMapper.selectOne(new LambdaQueryWrapper<IotAlert>()
                .eq(IotAlert::getId, id).eq(IotAlert::getTenantId, tenantId));
        if (a == null) throw new BusinessException("告警不存在");
        IotAlertVO vo = toVO(a);
        if (a.getRuleId() != null) {
            IotRule r = ruleMapper.selectById(a.getRuleId());
            if (r != null) vo.setRuleName(r.getRuleName());
        }
        return vo;
    }

    @Override
    public void handle(Long id, HandleAlertDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        IotAlert a = alertMapper.selectOne(new LambdaQueryWrapper<IotAlert>()
                .eq(IotAlert::getId, id).eq(IotAlert::getTenantId, tenantId));
        if (a == null) throw new BusinessException("告警不存在");
        if (dto.getStatus() != 1 && dto.getStatus() != 2) throw new BusinessException("status 必须为 1/2");
        a.setStatus(dto.getStatus());
        a.setHandleTime(LocalDateTime.now());
        a.setHandler(currentUsername());
        a.setHandleRemark(dto.getRemark());
        alertMapper.updateById(a);
    }

    @Override
    public Map<String, Object> stats() {
        Long tenantId = TenantContext.getTenantId();
        Map<String, Object> result = new HashMap<>();
        // 按状态统计
        for (int s = 0; s <= 2; s++) {
            Long count = alertMapper.selectCount(new LambdaQueryWrapper<IotAlert>()
                    .eq(IotAlert::getTenantId, tenantId)
                    .eq(IotAlert::getStatus, s));
            result.put("status_" + s, count);
        }
        // 按级别
        for (String level : new String[]{"INFO", "WARN", "ERROR", "CRITICAL"}) {
            Long count = alertMapper.selectCount(new LambdaQueryWrapper<IotAlert>()
                    .eq(IotAlert::getTenantId, tenantId)
                    .eq(IotAlert::getLevel, level));
            result.put("level_" + level, count);
        }
        return result;
    }

    private String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }

    private IotAlertVO toVO(IotAlert a) {
        IotAlertVO vo = new IotAlertVO();
        BeanUtil.copyProperties(a, vo);
        return vo;
    }
}