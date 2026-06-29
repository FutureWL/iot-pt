package com.iot.platform.system.organization.service;
import com.iot.platform.system.organization.entity.SysOrganization;
import com.iot.platform.system.organization.vo.SysOrganizationVO;
import java.util.List;
public interface SysOrganizationService {
    List<SysOrganizationVO> tree();
    Long create(SysOrganization dto);
    void update(SysOrganization dto);
    void delete(Long id);
}