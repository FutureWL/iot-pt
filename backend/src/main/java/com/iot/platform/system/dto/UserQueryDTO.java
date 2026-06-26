package com.iot.platform.system.dto;

import lombok.Data;

/**
 * 用户分页查询
 */
@Data
public class UserQueryDTO {

    /** 页码,从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 关键字:匹配 username / nickname / phone / email */
    private String keyword;

    /** 按状态过滤,null = 全部 */
    private Integer status;
}