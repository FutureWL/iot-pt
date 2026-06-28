package com.iot.platform.system.organization.vo;
import lombok.Data; import java.io.Serializable; import java.util.List;
@Data public class SysOrganizationVO implements Serializable {
    private Long id, parentId; private String name, path; private Integer sort;
    private String leader, phone, description, createdAt; private List<SysOrganizationVO> children;
}
