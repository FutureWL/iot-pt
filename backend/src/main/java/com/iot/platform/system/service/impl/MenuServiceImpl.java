package com.iot.platform.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.system.entity.SysMenu;
import com.iot.platform.system.mapper.SysMenuMapper;
import com.iot.platform.system.service.MenuService;
import com.iot.platform.system.vo.SysMenuTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final SysMenuMapper menuMapper;

    @Override
    public List<SysMenuTreeVO> tree() {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSort));

        List<SysMenuTreeVO> vos = all.stream().map(m -> {
            SysMenuTreeVO vo = new SysMenuTreeVO();
            BeanUtil.copyProperties(m, vo);
            return vo;
        }).collect(Collectors.toList());

        // 拼成树
        List<SysMenuTreeVO> roots = vos.stream()
                .filter(v -> v.getParentId() == null || v.getParentId() == 0L)
                .sorted(Comparator.comparing(SysMenuTreeVO::getSort,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        for (SysMenuTreeVO root : roots) {
            root.setChildren(buildChildren(root.getId(), vos));
        }
        return roots;
    }

    private List<SysMenuTreeVO> buildChildren(Long parentId, List<SysMenuTreeVO> all) {
        List<SysMenuTreeVO> kids = all.stream()
                .filter(v -> parentId.equals(v.getParentId()))
                .sorted(Comparator.comparing(SysMenuTreeVO::getSort,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        for (SysMenuTreeVO kid : kids) {
            kid.setChildren(buildChildren(kid.getId(), all));
        }
        return kids;
    }
}