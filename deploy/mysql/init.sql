-- ============================================================
-- MySQL 初始化
-- 容器首次启动时自动执行(在 MYSQL_DATABASE 创建后)
-- ============================================================
-- 设计要点:
-- 1. 所有业务表都带 tenant_id,实现多租户隔离
-- 2. 使用雪花 ID(ASSIGN_ID)作为主键
-- 3. 通用字段: created_at / updated_at / deleted (逻辑删除)
-- 4. 物模型、产品、设备是核心三张表
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE iot_platform;

-- ----------------------------
-- 1. 租户
-- ----------------------------
DROP TABLE IF EXISTS sys_tenant;
CREATE TABLE sys_tenant (
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_code  VARCHAR(64)  NOT NULL,
    tenant_name  VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    contact_email VARCHAR(128),
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    expire_time  DATETIME     COMMENT '到期时间,null=永久',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- ----------------------------
-- 2. 用户
-- ----------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL COMMENT '所属租户',
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(128) NOT NULL COMMENT 'BCrypt',
    nickname     VARCHAR(64),
    avatar       VARCHAR(255),
    email        VARCHAR(128),
    phone        VARCHAR(32),
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    last_login_at DATETIME,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    -- 逻辑删除友好的唯一约束: deleted=1 时 uk_username 为 NULL,MySQL 不视为重复
    uk_username  VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN username ELSE NULL END) STORED,
    UNIQUE KEY uk_tenant_username (tenant_id, uk_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 3. 角色
-- ----------------------------
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    built_in    TINYINT      NOT NULL DEFAULT 0 COMMENT '1=内置 0=自定义',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_role (tenant_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ----------------------------
-- 4. 菜单/权限
-- ----------------------------
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
    id          BIGINT       NOT NULL PRIMARY KEY,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    menu_name   VARCHAR(64)  NOT NULL,
    menu_type   TINYINT      NOT NULL COMMENT '1=目录 2=菜单 3=按钮',
    path        VARCHAR(255),
    component   VARCHAR(255),
    icon        VARCHAR(64),
    sort        INT          NOT NULL DEFAULT 0,
    permission  VARCHAR(128) COMMENT '权限标识,如 device:add',
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单/权限表';

-- ----------------------------
-- 5. 用户-角色
-- ----------------------------
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

-- ----------------------------
-- 6. 角色-菜单
-- ----------------------------
DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单';

-- ----------------------------
-- 7. 产品
-- ----------------------------
DROP TABLE IF EXISTS iot_product;
CREATE TABLE iot_product (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    product_key     VARCHAR(32)  NOT NULL COMMENT '产品Key(全局唯一)',
    product_name    VARCHAR(128) NOT NULL,
    category        VARCHAR(64),
    description     VARCHAR(500),
    auth_type       VARCHAR(16)  NOT NULL DEFAULT 'deviceSecret' COMMENT 'deviceSecret / dynamic / none',
    node_type       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=直连 1=网关 2=网关子设备',
    net_type        VARCHAR(16)  NOT NULL DEFAULT 'MQTT' COMMENT 'MQTT/TCP',
    status          TINYINT      NOT NULL DEFAULT 1,
    icon            VARCHAR(255),
    -- 物模型 JSON(属性/事件/服务 定义,见 docs/thing-model.md)
    thing_model     JSON         NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    -- 逻辑删除友好的唯一约束
    uk_product_key  VARCHAR(32)  GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN product_key ELSE NULL END) STORED,
    UNIQUE KEY uk_product_key (product_key),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品/物模型';

-- ----------------------------
-- 8. 设备分组
-- ----------------------------
DROP TABLE IF EXISTS iot_device_group;
CREATE TABLE iot_device_group (
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    group_name  VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    sort        INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分组';

-- ----------------------------
-- 9. 设备
-- ----------------------------
DROP TABLE IF EXISTS iot_device;
CREATE TABLE iot_device (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    group_id        BIGINT       NOT NULL DEFAULT 0,
    device_key      VARCHAR(64)  NOT NULL COMMENT '设备唯一标识(通常为 MAC/序列号)',
    device_name     VARCHAR(128) NOT NULL,
    device_secret   VARCHAR(64)  NOT NULL,
    protocol        VARCHAR(16)  NOT NULL DEFAULT 'MQTT',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=离线 1=在线 2=禁用',
    active_time     DATETIME     COMMENT '首次激活时间',
    last_online_time DATETIME,
    last_offline_time DATETIME,
    ip_address      VARCHAR(64),
    firmware_version VARCHAR(32),
    location        VARCHAR(255),
    tags            VARCHAR(500),
    description     VARCHAR(500),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    -- 逻辑删除友好的唯一约束
    uk_tenant_device_key VARCHAR(132) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN CONCAT(tenant_id, ':', device_key) ELSE NULL END
    ) STORED,
    UNIQUE KEY uk_tenant_device_key (uk_tenant_device_key),
    KEY idx_product (product_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- ----------------------------
-- 10. 设备影子(物模型属性当前值)
-- ----------------------------
DROP TABLE IF EXISTS iot_device_property;
CREATE TABLE iot_device_property (
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    device_id    BIGINT       NOT NULL,
    identifier   VARCHAR(64)  NOT NULL COMMENT '物模型属性标识',
    value_json   JSON         NOT NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_identifier (device_id, identifier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备属性当前值(影子)';

-- ----------------------------
-- 11. 规则引擎
-- ----------------------------
DROP TABLE IF EXISTS iot_rule;
CREATE TABLE iot_rule (
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    rule_name    VARCHAR(128) NOT NULL,
    description  VARCHAR(500),
    trigger_type VARCHAR(32)  NOT NULL COMMENT 'data/property/event/online/offline/timer',
    -- 过滤条件(SpEL 表达式)
    filter_expr  TEXT         NOT NULL,
    -- 动作列表 JSON: [{type, ...params}]
    actions      JSON         NOT NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_tenant (tenant_id),
    KEY idx_trigger (trigger_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则引擎';

-- ----------------------------
-- 12. 告警
-- ----------------------------
DROP TABLE IF EXISTS iot_alert;
CREATE TABLE iot_alert (
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    rule_id      BIGINT,
    device_id    BIGINT,
    device_key   VARCHAR(64),
    product_key  VARCHAR(32),
    level        VARCHAR(16)  NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARN/ERROR/CRITICAL',
    title        VARCHAR(255) NOT NULL,
    content      TEXT,
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未处理 1=已处理 2=已忽略',
    handler      VARCHAR(64),
    handle_time  DATETIME,
    handle_remark VARCHAR(500),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_tenant (tenant_id),
    KEY idx_device (device_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录';

-- ----------------------------
-- 13. 通知渠道
-- ----------------------------
DROP TABLE IF EXISTS iot_notify_channel;
CREATE TABLE iot_notify_channel (
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    channel_name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32)  NOT NULL COMMENT 'dingtalk/wechat/webhook/email/sms',
    config       JSON         NOT NULL COMMENT '渠道配置 JSON',
    status       TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道';

-- ----------------------------
-- 14. 字典(设备状态、协议类型等枚举)
-- ----------------------------
DROP TABLE IF EXISTS sys_dict;
CREATE TABLE sys_dict (
    id         BIGINT       NOT NULL PRIMARY KEY,
    dict_type  VARCHAR(64)  NOT NULL,
    dict_label VARCHAR(64)  NOT NULL,
    dict_value VARCHAR(64)  NOT NULL,
    sort       INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 初始数据
-- ============================================================

-- 超级租户
INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, status)
VALUES (1, 'default', '默认租户', '管理员', 1);

-- 超级管理员(admin / 123456)
-- BCrypt 加密的 "123456"
INSERT INTO sys_user (id, tenant_id, username, password, nickname, email, status)
VALUES (1, 1, 'admin', '$2b$10$MhwXrkbWfHrjJFsZZWbJYupW2L4OQ5pr76OdTpwxogq7vF/sLH2om', '超级管理员', 'admin@iot.local', 1);

-- 内置角色
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, built_in)
VALUES
    (1, 1, 'SUPER_ADMIN', '超级管理员', '系统内置', 1),
    (2, 1, 'TENANT_ADMIN', '租户管理员', '系统内置', 1),
    (3, 1, 'NORMAL', '普通用户', '系统内置', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 菜单(完整功能树)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, permission) VALUES
    (1,  0, '工作台',     2, '/dashboard',  'dashboard/Index',     'odometer',     1, 'dashboard:view'),
    (2,  0, '设备管理',   1, '/device',     'Layout',              'monitor',      2, NULL),
    (3,  2, '设备列表',   2, '/device/list','device/List',         NULL,           1, 'device:list'),
    (4,  2, '设备分组',   2, '/device/group','device/Group',       NULL,           2, 'device:group'),
    (5,  2, '设备影子',   2, '/device/shadow','device/Shadow',      NULL,           3, 'device:shadow'),
    (10, 0, '产品管理',   2, '/product',    'product/Index',       'box',          3, 'product:list'),
    (20, 0, '数据管理',   1, '/data',       'Layout',              'data-line',    4, NULL),
    (21, 20, '实时数据',   2, '/data/realtime','data/Realtime',     NULL,           1, 'data:realtime'),
    (22, 20, '历史数据',   2, '/data/history','data/History',       NULL,           2, 'data:history'),
    (30, 0, '规则引擎',   1, '/rule',       'Layout',              'set-up',       5, NULL),
    (31, 30, '规则列表',   2, '/rule/list',  'rule/List',           NULL,           1, 'rule:list'),
    (32, 30, '告警记录',   2, '/rule/alert', 'rule/Alert',          NULL,           2, 'rule:alert'),
    (40, 0, '可视化大屏', 2, '/screen',     'screen/Index',        'pie-chart',    6, 'screen:view'),
    (50, 0, '系统管理',   1, '/system',     'Layout',              'setting',      7, NULL),
    (51, 50, '用户管理',   2, '/system/user','system/User',         NULL,           1, 'system:user'),
    (52, 50, '角色管理',   2, '/system/role','system/Role',         NULL,           2, 'system:role'),
    (53, 50, '菜单管理',   2, '/system/menu','system/Menu',         NULL,           3, 'system:menu'),
    (54, 50, '租户管理',   2, '/system/tenant','system/Tenant',     NULL,           4, 'system:tenant'),
    (55, 50, '通知渠道',   2, '/system/notify','system/Notify',     NULL,           5, 'system:notify');

-- 超级管理员拥有所有菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 字典
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort) VALUES
    ('device_status', '离线',  '0', 1),
    ('device_status', '在线',  '1', 2),
    ('device_status', '禁用',  '2', 3),
    ('protocol',      'MQTT',  'MQTT', 1),
    ('protocol',      'TCP',   'TCP',  2),
    ('alert_level',   '提示',  'INFO',     1),
    ('alert_level',   '警告',  'WARN',     2),
    ('alert_level',   '严重',  'ERROR',    3),
    ('alert_level',   '紧急',  'CRITICAL', 4),
    ('node_type',     '直连设备', '0', 1),
    ('node_type',     '网关',     '1', 2),
    ('node_type',     '网关子设备','2', 3);
