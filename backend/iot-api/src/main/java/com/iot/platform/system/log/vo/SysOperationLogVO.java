package com.iot.platform.system.log.vo;
import lombok.Data; import java.io.Serializable;
@Data public class SysOperationLogVO implements Serializable {
    private Long id, userId, costMs; private String username, module, action;
    private String method, url, ip, userAgent, params, ts; private Integer status;
}
