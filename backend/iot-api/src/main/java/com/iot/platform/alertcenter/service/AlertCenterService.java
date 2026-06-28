package com.iot.platform.alertcenter.service;
import com.iot.platform.alertcenter.vo.AlertCenterVO;
import com.iot.platform.alertcenter.vo.AlertLevelStatVO;
import java.util.List; import java.util.Map;
public interface AlertCenterService {
    List<AlertLevelStatVO> stats();
    Map<String, Object> page(Map<String, Object> params);
}
