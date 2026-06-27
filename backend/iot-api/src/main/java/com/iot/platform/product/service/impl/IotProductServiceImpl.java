package com.iot.platform.product.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.common.BusinessException;
import com.iot.platform.product.dto.IotProductDTO;
import com.iot.platform.product.dto.IotProductQueryDTO;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import com.iot.platform.product.service.IotProductService;
import com.iot.platform.product.vo.IotProductVO;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IotProductServiceImpl implements IotProductService {

    private final IotProductMapper productMapper;

    @Override
    public IPage<IotProductVO> page(IotProductQueryDTO q) {
        Long tenantId = TenantContext.getTenantId();
        Page<IotProduct> page = new Page<>(
                q.getPageNum() == null ? 1 : q.getPageNum(),
                q.getPageSize() == null ? 10 : q.getPageSize()
        );
        LambdaQueryWrapper<IotProduct> w = new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getTenantId, tenantId)
                .eq(q.getStatus() != null, IotProduct::getStatus, q.getStatus())
                .eq(StrUtil.isNotBlank(q.getCategory()), IotProduct::getCategory, q.getCategory())
                .eq(StrUtil.isNotBlank(q.getNetType()), IotProduct::getNetType, q.getNetType())
                .and(StrUtil.isNotBlank(q.getKeyword()), ww -> ww
                        .like(IotProduct::getProductKey, q.getKeyword())
                        .or().like(IotProduct::getProductName, q.getKeyword())
                        .or().like(IotProduct::getDescription, q.getKeyword()))
                .orderByDesc(IotProduct::getCreatedAt);
        IPage<IotProduct> res = productMapper.selectPage(page, w);
        return res.convert(this::toVO);
    }

    @Override
    public List<IotProductVO> all() {
        Long tenantId = TenantContext.getTenantId();
        List<IotProduct> list = productMapper.selectList(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getTenantId, tenantId)
                .eq(IotProduct::getStatus, 1)
                .orderByDesc(IotProduct::getCreatedAt));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public IotProductVO detail(Long id) {
        IotProduct p = mustGet(id);
        return toVO(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(IotProductDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        // 唯一性 (productKey 全局唯一,索引已支持逻辑删除)
        Long dup = productMapper.selectCount(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getProductKey, dto.getProductKey()));
        if (dup != null && dup > 0) {
            throw new BusinessException("产品 Key 已存在");
        }
        IotProduct p = new IotProduct();
        BeanUtil.copyProperties(dto, p);
        p.setTenantId(tenantId);
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getNodeType() == null) p.setNodeType(0);
        if (StrUtil.isBlank(p.getThingModel())) {
            p.setThingModel(defaultThingModel());
        }
        productMapper.insert(p);
        log.info("创建产品: key={}", dto.getProductKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(IotProductDTO dto) {
        if (dto.getId() == null) throw new BusinessException("id 不能为空");
        IotProduct exist = mustGet(dto.getId());

        // 改名则校验唯一
        if (!exist.getProductKey().equals(dto.getProductKey())) {
            Long dup = productMapper.selectCount(new LambdaQueryWrapper<IotProduct>()
                    .eq(IotProduct::getProductKey, dto.getProductKey())
                    .ne(IotProduct::getId, dto.getId()));
            if (dup != null && dup > 0) throw new BusinessException("产品 Key 已存在");
        }

        exist.setProductKey(dto.getProductKey());
        exist.setProductName(dto.getProductName());
        exist.setCategory(dto.getCategory());
        exist.setDescription(dto.getDescription());
        exist.setAuthType(dto.getAuthType());
        if (dto.getNodeType() != null) exist.setNodeType(dto.getNodeType());
        exist.setNetType(dto.getNetType());
        if (dto.getStatus() != null) exist.setStatus(dto.getStatus());
        exist.setIcon(dto.getIcon());
        if (StrUtil.isNotBlank(dto.getThingModel())) {
            exist.setThingModel(dto.getThingModel());
        }
        productMapper.updateById(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        IotProduct exist = mustGet(id);
        productMapper.deleteById(exist.getId());
        log.info("删除产品: id={}, key={}", id, exist.getProductKey());
    }

    @Override
    public String defaultThingModel() {
        return "{\"properties\":[],\"events\":[],\"services\":[]}";
    }

    // ============ 辅助 ============

    private IotProductVO toVO(IotProduct p) {
        IotProductVO vo = new IotProductVO();
        BeanUtil.copyProperties(p, vo);
        if (StrUtil.isBlank(vo.getThingModel())) {
            vo.setThingModel(defaultThingModel());
        }
        return vo;
    }

    private IotProduct mustGet(Long id) {
        Long tenantId = TenantContext.getTenantId();
        IotProduct p = productMapper.selectOne(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getId, id)
                .eq(IotProduct::getTenantId, tenantId));
        if (p == null) throw new BusinessException("产品不存在");
        return p;
    }
}