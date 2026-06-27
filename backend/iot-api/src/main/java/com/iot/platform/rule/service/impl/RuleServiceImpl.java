package com.iot.platform.rule.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.common.BusinessException;
import com.iot.platform.rule.dto.IotRuleDTO;
import com.iot.platform.rule.dto.IotRuleQueryDTO;
import com.iot.platform.rule.entity.IotRule;
import com.iot.platform.rule.mapper.IotRuleMapper;
import com.iot.platform.rule.service.RuleService;
import com.iot.platform.rule.vo.IotRuleVO;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleServiceImpl implements RuleService {

    private final IotRuleMapper ruleMapper;

    @Override
    public IPage<IotRuleVO> page(IotRuleQueryDTO q) {
        Long tenantId = TenantContext.getTenantId();
        Page<IotRule> page = new Page<>(
                q.getPageNum() == null ? 1 : q.getPageNum(),
                q.getPageSize() == null ? 10 : q.getPageSize()
        );
        LambdaQueryWrapper<IotRule> w = new LambdaQueryWrapper<IotRule>()
                .eq(IotRule::getTenantId, tenantId)
                .eq(StrUtil.isNotBlank(q.getTriggerType()), IotRule::getTriggerType, q.getTriggerType())
                .eq(q.getStatus() != null, IotRule::getStatus, q.getStatus())
                .and(StrUtil.isNotBlank(q.getKeyword()), ww -> ww
                        .like(IotRule::getRuleName, q.getKeyword())
                        .or().like(IotRule::getDescription, q.getKeyword()))
                .orderByDesc(IotRule::getCreatedAt);
        IPage<IotRule> res = ruleMapper.selectPage(page, w);
        return res.convert(this::toVO);
    }

    @Override
    public IotRuleVO detail(Long id) {
        IotRule r = mustGet(id);
        return toVO(r);
    }

    @Override
    public void create(IotRuleDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        Long dup = ruleMapper.selectCount(new LambdaQueryWrapper<IotRule>()
                .eq(IotRule::getTenantId, tenantId)
                .eq(IotRule::getRuleName, dto.getRuleName()));
        if (dup != null && dup > 0) throw new BusinessException("规则名已存在");

        IotRule r = new IotRule();
        BeanUtil.copyProperties(dto, r);
        r.setTenantId(tenantId);
        if (r.getStatus() == null) r.setStatus(1);
        ruleMapper.insert(r);
        log.info("创建规则: {}", dto.getRuleName());
    }

    @Override
    public void update(IotRuleDTO dto) {
        if (dto.getId() == null) throw new BusinessException("id 不能为空");
        IotRule exist = mustGet(dto.getId());
        BeanUtil.copyProperties(dto, exist);
        ruleMapper.updateById(exist);
    }

    @Override
    public void delete(Long id) {
        IotRule exist = mustGet(id);
        ruleMapper.deleteById(id);
        log.info("删除规则: {}", exist.getRuleName());
    }

    @Override
    public void toggle(Long id, Integer status) {
        IotRule exist = mustGet(id);
        if (status == null || (status != 0 && status != 1)) throw new BusinessException("status 必须为 0/1");
        exist.setStatus(status);
        ruleMapper.updateById(exist);
    }

    private IotRuleVO toVO(IotRule r) {
        IotRuleVO vo = new IotRuleVO();
        BeanUtil.copyProperties(r, vo);
        return vo;
    }

    private IotRule mustGet(Long id) {
        Long tenantId = TenantContext.getTenantId();
        IotRule r = ruleMapper.selectOne(new LambdaQueryWrapper<IotRule>()
                .eq(IotRule::getId, id).eq(IotRule::getTenantId, tenantId));
        if (r == null) throw new BusinessException("规则不存在");
        return r;
    }
}