package com.iot.platform.system.dict.vo;
import lombok.Data; import java.io.Serializable;
@Data public class SysDictTypeVO implements Serializable {
    private Long id; private String type, typeName, description; private Integer status;
}
