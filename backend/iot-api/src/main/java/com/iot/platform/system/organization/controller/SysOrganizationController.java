package com.iot.platform.system.organization.controller;
import com.iot.platform.common.R;
import com.iot.platform.system.organization.service.SysOrganizationService;
import com.iot.platform.system.organization.vo.SysOrganizationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/system/organization") @RequiredArgsConstructor
public class SysOrganizationController {
    private final SysOrganizationService service;
    @GetMapping("/tree") public R<List<SysOrganizationVO>> tree() { return R.ok(service.tree()); }
}
