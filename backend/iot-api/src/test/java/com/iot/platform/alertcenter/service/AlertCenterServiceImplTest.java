package com.iot.platform.alertcenter.service;

import com.iot.platform.alertcenter.service.impl.AlertCenterServiceImpl;
import com.iot.platform.rule.mapper.IotAlertMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertCenterServiceImplTest {
    @Mock private IotAlertMapper alertMapper;
    @InjectMocks private AlertCenterServiceImpl service;

    @Test
    void pageShouldHandleStringParamsFromRequest() {
        Map<String, Object> params = new HashMap<>();
        params.put("current", "1");
        params.put("size", "10");
        params.put("level", "URGENT");
        when(alertMapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        Map<String, Object> result = service.page(params);
        assertThat(result).isNotNull();
        assertThat(result).containsKey("records");
    }
}
