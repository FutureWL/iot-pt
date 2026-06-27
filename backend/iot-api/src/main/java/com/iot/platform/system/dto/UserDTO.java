package com.iot.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户创建 / 更新请求
 *
 * <p>新建时 password 必填;更新时 password 留空表示不修改</p>
 */
@Data
public class UserDTO {

    /** 编辑时必传,新建时为空 */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 32, message = "用户名长度 2-32")
    private String username;

    /** 仅新建时必填;编辑时为空表示不修改 */
    private String password;

    @Size(max = 32, message = "昵称最多 32 字")
    private String nickname;

    private String email;
    private String phone;

    /** 1=启用 0=禁用 */
    private Integer status;
}