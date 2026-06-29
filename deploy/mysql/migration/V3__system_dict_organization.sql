-- ============================================================
-- 系统管理:字典管理 + 组织架构 补齐
-- V3 — 2026-06-29
--
-- 历史背景: V1 已有 workorder/topology,V2 已有 knowledge_base,
--           但 sys_dict_type / sys_dict_item / sys_organization 一直缺失,
--           导致 system/dict 与 system/organization 页面 CRUD 必报 500
--           (前端调 POST /system/dict/type → "No static resource")。
--           本次补齐 3 张表 + 示例数据,后续 backend service/controller
--           才有真实数据可读写。
-- ============================================================
SET NAMES utf8mb4;

-- ============================================================
-- 字典类型
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_dict_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(64) NOT NULL COMMENT '字典类型编码(英文,程序引用)',
  type_name VARCHAR(128) NOT NULL COMMENT '字典类型名称(显示用)',
  description VARCHAR(500) COMMENT '说明',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_type (type),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

-- ============================================================
-- 字典项(挂在某个 type 下)
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_dict_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(64) NOT NULL COMMENT '字典类型编码(关联 sys_dict_type.type)',
  code VARCHAR(128) NOT NULL COMMENT '字典项编码(type 内唯一)',
  label VARCHAR(255) NOT NULL COMMENT '显示名',
  value VARCHAR(255) NOT NULL COMMENT '值',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
  description VARCHAR(500) COMMENT '说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_type_code (type, code),
  KEY idx_type (type),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项';

-- ============================================================
-- 组织架构(支持树形:parent_id=0 表示顶级)
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_organization (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父组织 id,0=顶级',
  name VARCHAR(128) NOT NULL COMMENT '组织名称',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
  leader VARCHAR(64) COMMENT '负责人',
  phone VARCHAR(32) COMMENT '联系电话',
  description VARCHAR(500) COMMENT '说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_parent (parent_id),
  KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织架构';

-- ============================================================
-- 示例数据
-- ============================================================
-- 字典类型
INSERT INTO sys_dict_type (type, type_name, description, status) VALUES
  ('device_status', '设备状态', '设备在线/离线/禁用 状态', 1),
  ('alert_level', '告警级别', '电力告警级别:注意/异常/严重/紧急', 1),
  ('user_status', '用户状态', '用户启/停状态', 1);

-- 字典项(device_status)
INSERT INTO sys_dict_item (type, code, label, value, sort, status) VALUES
  ('device_status', 'offline',  '离线', '0', 1, 1),
  ('device_status', 'online',   '在线', '1', 2, 1),
  ('device_status', 'disabled', '禁用', '2', 3, 1);

-- 字典项(alert_level)
INSERT INTO sys_dict_item (type, code, label, value, sort, status) VALUES
  ('alert_level', 'notice',   '注意',  'NOTICE',   1, 1),
  ('alert_level', 'abnormal', '异常',  'ABNORMAL', 2, 1),
  ('alert_level', 'serious',  '严重',  'SERIOUS',  3, 1),
  ('alert_level', 'urgent',   '紧急',  'URGENT',   4, 1);

-- 字典项(user_status)
INSERT INTO sys_dict_item (type, code, label, value, sort, status) VALUES
  ('user_status', 'disabled', '禁用', '0', 1, 1),
  ('user_status', 'enabled',  '启用', '1', 2, 1);

-- 组织架构(3 级示例)
INSERT INTO sys_organization (id, parent_id, name, sort, leader, phone) VALUES
  (1, 0, '总部',     1, '张总',   '13800000001'),
  (2, 1, '研发中心', 1, '李经理', '13800000002'),
  (3, 1, '运维中心', 2, '王经理', '13800000003'),
  (4, 2, '前端组',   1, '陈组长', '13800000004'),
  (5, 2, '后端组',   2, '刘组长', '13800000005'),
  (6, 3, '现场运维', 1, '赵组长', '13800000006'),
  (7, 3, '客服组',   2, '孙组长', '13800000007');