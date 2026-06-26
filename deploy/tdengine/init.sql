-- ============================================================
-- TDengine 初始化 - 时序数据库
-- 设计原则:
-- 1. 设备属性按 identifier 分超级表(物模型属性各自一张表)
-- 2. 每个子表 = 一个设备的一个属性,tag 是 device_id, product_key
-- 3. 事件用单独的超级表
-- ============================================================

-- 库
CREATE DATABASE IF NOT EXISTS iot_data PRECISION 'ms' BUFFER 16 CACHEMODEL 'last_row' COMP 2;

USE iot_data;

-- 通用属性时序超级表模板
-- 不同数据类型(int/float/bool/string)使用不同超级表避免类型混杂
-- 也可考虑用 binary 通用,但查询效率下降。这里用 4 个并行的超表,
-- 由应用层根据物模型类型写入对应表。

-- INT 类型属性
CREATE STABLE IF NOT EXISTS iot_prop_int (
    ts           TIMESTAMP,
    value        INT
) TAGS (
    tenant_id    BIGINT,
    product_key  BINARY(32),
    device_id    BIGINT,
    device_key   BINARY(64),
    identifier   BINARY(64)
);

-- BIGINT
CREATE STABLE IF NOT EXISTS iot_prop_bigint (
    ts           TIMESTAMP,
    value        BIGINT
) TAGS (
    tenant_id    BIGINT,
    product_key  BINARY(32),
    device_id    BIGINT,
    device_key   BINARY(64),
    identifier   BINARY(64)
);

-- DOUBLE
CREATE STABLE IF NOT EXISTS iot_prop_double (
    ts           TIMESTAMP,
    value        DOUBLE
) TAGS (
    tenant_id    BIGINT,
    product_key  BINARY(32),
    device_id    BIGINT,
    device_key   BINARY(64),
    identifier   BINARY(64)
);

-- BOOL
CREATE STABLE IF NOT EXISTS iot_prop_bool (
    ts           TIMESTAMP,
    value        BOOL
) TAGS (
    tenant_id    BIGINT,
    product_key  BINARY(32),
    device_id    BIGINT,
    device_key   BINARY(64),
    identifier   BINARY(64)
);

-- STRING
CREATE STABLE IF NOT EXISTS iot_prop_string (
    ts           TIMESTAMP,
    value        NCHAR(255)
) TAGS (
    tenant_id    BIGINT,
    product_key  BINARY(32),
    device_id    BIGINT,
    device_key   BINARY(64),
    identifier   BINARY(64)
);

-- 事件时序超级表
CREATE STABLE IF NOT EXISTS iot_event (
    ts           TIMESTAMP,
    event_id     BINARY(64),
    output_json  NCHAR(2048)
) TAGS (
    tenant_id    BIGINT,
    product_key  BINARY(32),
    device_id    BIGINT,
    device_key   BINARY(64),
    event_name   BINARY(64)
);
