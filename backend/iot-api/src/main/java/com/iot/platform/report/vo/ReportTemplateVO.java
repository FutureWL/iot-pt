package com.iot.platform.report.vo;
import lombok.Data; import java.io.Serializable;
@Data public class ReportTemplateVO implements Serializable {
    private Long id; private String templateName, reportType, description, paramsSchema;
}
