package com.iot.platform.system.organization.service.impl;
import com.iot.platform.system.organization.service.SysOrganizationService;
import com.iot.platform.system.organization.vo.SysOrganizationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections; import java.util.List;
@Service @RequiredArgsConstructor
public class SysOrganizationServiceImpl implements SysOrganizationService {
    @Override public List<SysOrganizationVO> tree() { return Collections.emptyList(); }
}
