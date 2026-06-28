-- 工单 + 拓扑模块数据库迁移
-- V1 — 2026-06-28 补齐工单与电网拓扑

CREATE TABLE work_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  work_order_no VARCHAR(64) NOT NULL UNIQUE,
  alert_id BIGINT,
  device_id BIGINT NOT NULL,
  device_key VARCHAR(128),
  device_name VARCHAR(255),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  priority VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  assignee VARCHAR(64),
  creator VARCHAR(64) NOT NULL,
  sla_deadline DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME,
  KEY idx_status (status),
  KEY idx_device (device_id),
  KEY idx_assignee (assignee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单';

CREATE TABLE work_order_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  work_order_id BIGINT NOT NULL,
  operator VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  remark TEXT,
  ts DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_work_order (work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单操作日志';

CREATE TABLE topology_node (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(32) NOT NULL,
  voltage_level VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  device_id BIGINT,
  region VARCHAR(128),
  substation_code VARCHAR(64),
  properties JSON,
  KEY idx_region (region),
  KEY idx_device (device_id),
  KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电网拓扑节点';

CREATE TABLE topology_edge (
  id VARCHAR(64) PRIMARY KEY,
  source_id VARCHAR(64) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  label VARCHAR(255),
  KEY idx_source (source_id),
  KEY idx_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电网拓扑连接';
