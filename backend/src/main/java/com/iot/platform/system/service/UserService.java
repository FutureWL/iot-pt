package com.iot.platform.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.common.R;
import com.iot.platform.system.dto.ResetPasswordDTO;
import com.iot.platform.system.dto.UserDTO;
import com.iot.platform.system.dto.UserQueryDTO;
import com.iot.platform.system.vo.SysUserVO;

/**
 * 用户管理服务
 */
public interface UserService {

    /** 分页列表 */
    IPage<SysUserVO> page(UserQueryDTO query);

    /** 详情 */
    SysUserVO detail(Long id);

    /** 创建用户 */
    void create(UserDTO dto);

    /** 更新用户 */
    void update(UserDTO dto);

    /** 删除用户(逻辑删除) */
    void delete(Long id);

    /** 重置密码 */
    void resetPassword(Long id, ResetPasswordDTO dto);

    /** 切换状态(启/停) */
    void toggleStatus(Long id, Integer status);
}