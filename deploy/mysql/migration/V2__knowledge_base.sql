-- ============================================================
-- 知识库模块迁移
-- V2 — 2026-06-29 补齐 knowledge_base 表与示例数据
-- ============================================================
-- 注:用 docker exec 管道导入时务必加此句,否则中文会变乱码
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category VARCHAR(64) NOT NULL COMMENT '分类:故障处理/巡检作业/应急处置/设备维护/基础知识',
  title VARCHAR(255) NOT NULL COMMENT '标题',
  summary VARCHAR(500) COMMENT '摘要',
  content LONGTEXT COMMENT '正文(Markdown / 富文本)',
  tags VARCHAR(500) COMMENT '标签(英文逗号分隔)',
  version INT NOT NULL DEFAULT 1 COMMENT '版本号',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/PUBLISHED/ARCHIVED',
  author VARCHAR(64) NOT NULL COMMENT '作者',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_category (category),
  KEY idx_status (status),
  KEY idx_author (author),
  KEY idx_tenant (tenant_id),
  KEY idx_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

-- ============================================================
-- 示例数据(便于本地联调与手动验证)
-- ============================================================
INSERT IGNORE INTO knowledge_base (id, category, title, summary, content, tags, version, status, author) VALUES
(1, '故障处理', '变压器局部放电典型缺陷处理 SOP',
 '针对 10kV 油浸式变压器 PD 检测异常的判断、操作、验证流程',
 '# 适用范围\n\n10kV 油浸式变压器出厂/投运后 PD 检测异常。\n\n# 判断\n\n1. 放电幅值 > 50pC,持续 5 分钟以上\n2. 放电模式为内部放电(cluster 模式)\n3. 与历史数据对比无下降趋势\n\n# 操作\n\n1. 停电,挂接地线\n2. 油样采集送检(DGA 分析)\n3. 紫外成像定位放电点\n4. 视情况返厂检修\n\n# 验证\n\n- 送电后空载运行 24h,PD < 10pC\n- 油色谱稳定无异常',
 '局放,母排,10kV,变压器', 3, 'PUBLISHED', 'admin'),

(2, '巡检作业', '高压开关柜月度巡检标准作业指导书',
 '10kV 高压开关柜每月例行巡检项目与判定标准',
 '# 巡检项目\n\n1. 柜体外观、锈蚀情况\n2. 指示灯、仪表读数\n3. 温升(红外测温)\n4. 接地连接可靠性\n5. 电缆出线孔封堵\n\n# 判定标准\n\n| 项目 | 正常 | 异常 |\n| --- | --- | --- |\n| 母线温度 | < 60℃ | ≥ 70℃ |\n| 负荷电流 | < 额定 80% | ≥ 95% |',
 '巡检,高压柜,10kV,月度', 2, 'PUBLISHED', 'admin'),

(3, '应急处置', '主变跳闸应急处置预案',
 '110kV 主变保护动作跳闸后的现场处置流程',
 '# 信息确认\n\n1. 调取保护动作报告\n2. 确认故障相别、故障电流\n3. 隔离故障点\n\n# 现场处置\n\n1. 断开故障主变各侧刀闸\n2. 投入备用主变(若有)\n3. 调整运行方式\n\n# 后续\n\n- 油样采集送检\n- 故障录波上传\n- 编写事故报告',
 '应急,主变,跳闸', 1, 'PUBLISHED', 'admin'),

(4, '设备维护', 'GIS 设备 SF6 气体补充操作规程',
 'GIS 设备 SF6 压力低于额定值时的补气作业',
 '# 准备工作\n\n- SF6 气体(纯度 ≥ 99.99%)\n- 充气装置、真空泵\n- 个人防护\n\n# 操作步骤\n\n1. 确认阀门位置,关闭相关气室\n2. 连接充气管路,抽真空至 -0.1MPa\n3. 缓慢充气至额定压力(0.5MPa @ 20℃)\n4. 检漏\n\n# 安全注意\n\n- 通风良好\n- 严禁烟火\n- 作业人员持证',
 'GIS,SF6,维护', 1, 'PUBLISHED', 'admin'),

(5, '基础知识', '电力监控系统基本架构说明',
 'SCADA/EMS 系统分层架构与本平台定位',
 '# 分层\n\nL1 设备层:开关、传感器、继电保护\nL2 通信层:IEC61850、Modbus、MQTT\nL3 控制层:SCADA、EMS\nL4 应用层:数据分析、告警、工单\n\n# 本平台定位\n\n物联网平台位于 L2~L3,提供设备接入、影子服务、规则引擎。',
 '架构,SCADA,EMS', 1, 'DRAFT', 'admin'),

(6, '故障处理', '电缆接头发热缺陷处理流程',
 '10kV 电缆中间接头红外测温异常的处理',
 '# 现象\n\n红外测温显示电缆接头温度 > 70℃,与环境温差 > 30K。\n\n# 处理\n\n1. 申请停电\n2. 拆解检查\n3. 重新制作接头或更换\n4. 送电后复测',
 '电缆,接头,发热', 2, 'PUBLISHED', 'admin'),

(7, '巡检作业', '避雷器年度检测试验指导',
 '金属氧化物避雷器年度预防性试验',
 '# 试验项目\n\n1. 绝缘电阻测试\n2. 直流参考电压 U1mA\n3. 0.75 U1mA 下泄漏电流\n4. 工频参考电流\n\n# 判定\n\n| 项目 | 标准 |\n| --- | --- |\n| 绝缘电阻 | > 1000MΩ |\n| 0.75U1mA 泄漏电流 | < 50μA |',
 '避雷器,试验,年度', 1, 'PUBLISHED', 'admin'),

(8, '基础知识', '物联网 MQTT 协议基础',
 '本平台设备接入使用的 MQTT 协议基础概念',
 '# 核心概念\n\n- Topic: 主题(例:property/post)\n- QoS: 0/1/2 三种服务质量\n- Retain: 保留消息\n- Will: 遗嘱消息\n\n# 设备接入\n\n1. 三元组认证(ProductKey/DeviceKey/DeviceSecret)\n2. 上行:property/post,event/post\n3. 下行:property/set,service/invoke',
 'MQTT,物联网,协议', 1, 'PUBLISHED', 'admin');