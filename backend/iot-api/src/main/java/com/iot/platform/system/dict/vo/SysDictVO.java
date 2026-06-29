package com.iot.platform.system.dict.vo;
import lombok.Data; import java.io.Serializable;
@Data public class SysDictVO implements Serializable {
    private Long id; private String type, code, label, value, description;
    private Integer sort, status;
}