package com.iot.platform.alertcenter.vo;
import lombok.Data; import java.io.Serializable;
@Data public class AlertLevelStatVO implements Serializable { private String level; private Integer count; }
