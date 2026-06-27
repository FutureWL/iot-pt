"""PlatformClient 测试 (mock httpx,不发起真实请求)

覆盖:
  - 登录成功 / 失败(401、密码错、租户错)
  - 列产品(响应解包、字段映射)
  - 拉物模型(JSON 字符串二次解析)
  - 默认物模型模板
  - 网络错误(连接失败、超时)
"""
from __future__ import annotations

import json
from unittest.mock import AsyncMock, patch

import httpx
import pytest

from iot_simulator.api.platform_client import (
    Device,
    LoginResult,
    PlatformAuthError,
    PlatformClient,
    PlatformError,
    PlatformNetworkError,
    PlatformNotFoundError,
    Product,
)


def _mock_response(status_code: int = 200, body: dict | str | None = None) -> httpx.Response:
    if isinstance(body, str):
        return httpx.Response(status_code, text=body)
    return httpx.Response(status_code, json=body or {})


# ============================================================
# 登录
# ============================================================
@pytest.mark.asyncio
async def test_login_success() -> None:
    """登录成功,token 写入 client"""
    client = PlatformClient("http://localhost:33412")
    payload = {
        "code": 200,
        "message": "ok",
        "data": {
            "token": "jwt-test-abc",
            "userId": "1",
            "username": "admin",
            "tenantId": "1",
            "tenantCode": "default",
        },
        "timestamp": "1700000000000",
    }
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        result = await client.login("admin", "123456", "default")
        assert isinstance(result, LoginResult)
        assert result.token == "jwt-test-abc"
        assert result.username == "admin"
        assert result.tenant_code == "default"
        assert client.token == "jwt-test-abc"
    await client.close()


@pytest.mark.asyncio
async def test_login_wrong_password() -> None:
    """密码错 → PlatformAuthError"""
    client = PlatformClient("http://localhost:33412")
    payload = {"code": 400, "message": "用户名或密码错误", "data": None}
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        with pytest.raises(PlatformAuthError, match="用户名或密码"):
            await client.login("admin", "wrong", "default")
    await client.close()


@pytest.mark.asyncio
async def test_login_wrong_tenant() -> None:
    """租户错 → PlatformAuthError"""
    client = PlatformClient("http://localhost:33412")
    payload = {"code": 400, "message": "租户编码错误", "data": None}
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        with pytest.raises(PlatformAuthError, match="租户"):
            await client.login("admin", "123456", "wrong_tenant")
    await client.close()


@pytest.mark.asyncio
async def test_login_no_token_in_response() -> None:
    """登录返回 200 但 data 没有 token → PlatformAuthError"""
    client = PlatformClient("http://localhost:33412")
    payload = {"code": 200, "message": "ok", "data": {"userId": "1"}}
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        with pytest.raises(PlatformAuthError, match="无 token"):
            await client.login("admin", "123456", "default")
    await client.close()


@pytest.mark.asyncio
async def test_login_network_error() -> None:
    """连接失败 → PlatformNetworkError"""
    client = PlatformClient("http://localhost:33412")
    with patch.object(
        client._client, "request",
        new=AsyncMock(side_effect=httpx.ConnectError("Connection refused")),
    ):
        with pytest.raises(PlatformNetworkError, match="无法连接"):
            await client.login("admin", "123456", "default")
    await client.close()


@pytest.mark.asyncio
async def test_login_timeout() -> None:
    """超时 → PlatformNetworkError"""
    client = PlatformClient("http://localhost:33412")
    with patch.object(
        client._client, "request",
        new=AsyncMock(side_effect=httpx.TimeoutException("timeout")),
    ):
        with pytest.raises(PlatformNetworkError, match="超时"):
            await client.login("admin", "123456", "default")
    await client.close()


@pytest.mark.asyncio
async def test_login_http_401() -> None:
    """HTTP 401 → PlatformAuthError"""
    client = PlatformClient("http://localhost:33412")
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(401, "Unauthorized"))):
        with pytest.raises(PlatformAuthError, match="401"):
            await client.login("admin", "123456", "default")
    await client.close()


# ============================================================
# 列产品
# ============================================================
@pytest.mark.asyncio
async def test_list_products_success() -> None:
    client = PlatformClient("http://localhost:33412", token="jwt")
    payload = {
        "code": 200,
        "data": [
            {
                "id": "100",
                "productKey": "pk_temp",
                "productName": "温湿度传感器",
                "category": "传感器",
                "netType": "MQTT",
                "description": "工业级",
            },
            {
                "id": "200",
                "productKey": "pk_switch",
                "productName": "智能开关",
                "netType": "MQTT",
            },
        ],
    }
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        products = await client.list_products()
        assert len(products) == 2
        assert isinstance(products[0], Product)
        assert products[0].product_key == "pk_temp"
        assert products[0].category == "传感器"
        assert products[1].description == ""  # 缺失字段 → 空字符串
    await client.close()


@pytest.mark.asyncio
async def test_list_products_requires_login() -> None:
    client = PlatformClient("http://localhost:33412")
    with pytest.raises(PlatformAuthError, match="未登录"):
        await client.list_products()
    await client.close()


@pytest.mark.asyncio
async def test_list_products_empty() -> None:
    client = PlatformClient("http://localhost:33412", token="jwt")
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, {"code": 200, "data": []}))):
        products = await client.list_products()
        assert products == []
    await client.close()


# ============================================================
# 拉物模型
# ============================================================
@pytest.mark.asyncio
async def test_get_thing_model_json_string() -> None:
    """物模型字段是 JSON 字符串(后端实际格式),需要二次解析"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    tm_dict = {
        "properties": [
            {"identifier": "temperature", "name": "温度", "type": "float",
             "specs": {"min": "-40", "max": "125"}, "accessMode": "ro"}
        ],
        "events": [],
        "services": [],
    }
    payload = {
        "code": 200,
        "data": {"id": "100", "productKey": "pk", "thingModel": json.dumps(tm_dict)},
    }
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        tm = await client.get_thing_model("100")
        assert "properties" in tm
        assert tm["properties"][0]["identifier"] == "temperature"
    await client.close()


@pytest.mark.asyncio
async def test_get_thing_model_dict() -> None:
    """物模型字段直接是 dict(理论上后端不会这么返回,但容错)"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    tm_dict = {"properties": [{"identifier": "x"}], "events": [], "services": []}
    payload = {"code": 200, "data": {"thingModel": tm_dict}}
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        tm = await client.get_thing_model("100")
        assert tm["properties"][0]["identifier"] == "x"
    await client.close()


@pytest.mark.asyncio
async def test_get_thing_model_empty() -> None:
    """物模型为空(刚创建的产品)→ 返回空 dict,不报错"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    payload = {"code": 200, "data": {"thingModel": ""}}
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        tm = await client.get_thing_model("100")
        assert tm == {}
    await client.close()


@pytest.mark.asyncio
async def test_get_thing_model_not_found() -> None:
    client = PlatformClient("http://localhost:33412", token="jwt")
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(404))):
        with pytest.raises(PlatformNotFoundError):
            await client.get_thing_model("999")
    await client.close()


@pytest.mark.asyncio
async def test_get_thing_model_invalid_json() -> None:
    """物模型 JSON 字符串解析失败 → PlatformError"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    payload = {"code": 200, "data": {"thingModel": "{invalid json"}}
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        with pytest.raises(PlatformError, match="JSON 解析失败"):
            await client.get_thing_model("100")
    await client.close()


# ============================================================
# 上下文管理器
# ============================================================
@pytest.mark.asyncio
async def test_context_manager() -> None:
    async with PlatformClient("http://localhost:33412") as client:
        assert client is not None


# ============================================================
# URL 标准化
# ============================================================
def test_base_url_trailing_slash() -> None:
    client = PlatformClient("http://localhost:33412/")
    assert client.base_url == "http://localhost:33412"


# ============================================================
# 设备列表(密钥脱敏)
# ============================================================
@pytest.mark.asyncio
async def test_list_devices_by_product_success() -> None:
    """拉取产品下的设备列表,密钥脱敏"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    payload = {
        "code": 200,
        "data": {
            "records": [
                {
                    "id": "100",
                    "productId": "1",
                    "productKey": "pk_temp",
                    "deviceKey": "dev_001",
                    "deviceName": "测试设备001",
                    "deviceSecret": "abcd****1234",   # 脱敏
                    "protocol": "MQTT",
                    "status": 1,
                },
                {
                    "id": "101",
                    "productId": "1",
                    "productKey": "pk_temp",
                    "deviceKey": "dev_002",
                    "deviceSecret": "efgh****5678",
                    "status": 0,
                },
            ],
            "total": "2",
            "size": "100",
            "current": "1",
            "pages": "1",
        },
    }
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        devices = await client.list_devices_by_product("1")
        assert len(devices) == 2
        assert isinstance(devices[0], Device)
        assert devices[0].device_key == "dev_001"
        assert devices[0].is_secret_masked is True
        assert devices[0].status == 1
        assert devices[1].device_name == ""  # 缺失字段
    await client.close()


@pytest.mark.asyncio
async def test_list_devices_empty() -> None:
    """产品下没有设备 → 返回空列表"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    payload = {"code": 200, "data": {"records": [], "total": "0", "size": "100", "current": "1", "pages": "1"}}
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        devices = await client.list_devices_by_product("1")
        assert devices == []
    await client.close()


@pytest.mark.asyncio
async def test_list_devices_requires_login() -> None:
    client = PlatformClient("http://localhost:33412")
    with pytest.raises(PlatformAuthError, match="未登录"):
        await client.list_devices_by_product("1")
    await client.close()


@pytest.mark.asyncio
async def test_list_devices_empty_product_id() -> None:
    client = PlatformClient("http://localhost:33412", token="jwt")
    with pytest.raises(PlatformError, match="product_id"):
        await client.list_devices_by_product("")
    await client.close()


# ============================================================
# 设备详情(明文密钥)
# ============================================================
@pytest.mark.asyncio
async def test_get_device_full_success() -> None:
    """拉取设备详情,返回明文密钥"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    payload = {
        "code": 200,
        "data": {
            "id": "100",
            "productId": "1",
            "productKey": "pk_temp",
            "deviceKey": "dev_001",
            "deviceName": "测试设备001",
            "deviceSecret": "eb550ffc65d3413680e680d23b5a691c",   # 明文 32 位
            "protocol": "MQTT",
            "status": 1,
        },
    }
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        dev = await client.get_device_full("100")
        assert dev.device_key == "dev_001"
        assert dev.device_secret == "eb550ffc65d3413680e680d23b5a691c"
        assert dev.is_secret_masked is False
        assert len(dev.device_secret) == 32  # 32 位十六进制
    await client.close()


@pytest.mark.asyncio
async def test_get_device_full_not_found() -> None:
    client = PlatformClient("http://localhost:33412", token="jwt")
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(404))):
        with pytest.raises(PlatformNotFoundError):
            await client.get_device_full("999")
    await client.close()


@pytest.mark.asyncio
async def test_get_device_full_requires_login() -> None:
    client = PlatformClient("http://localhost:33412")
    with pytest.raises(PlatformAuthError, match="未登录"):
        await client.get_device_full("100")
    await client.close()


# ============================================================
# 新建设备
# ============================================================
@pytest.mark.asyncio
async def test_create_device_success() -> None:
    """新建设备,返回明文密钥"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    payload = {
        "code": 200,
        "data": {
            "id": "200",
            "productId": "1",
            "productKey": "pk_temp",
            "deviceKey": "dev_new",
            "deviceName": "新建设备",
            "deviceSecret": "1234567890abcdef1234567890abcdef",   # 后端自动生成
            "protocol": "MQTT",
            "status": 1,
        },
    }
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        dev = await client.create_device("1", "dev_new", "新建设备")
        assert dev.device_key == "dev_new"
        assert dev.device_name == "新建设备"
        assert dev.is_secret_masked is False
        assert len(dev.device_secret) == 32
    await client.close()


@pytest.mark.asyncio
async def test_create_device_invalid_key() -> None:
    """平台校验失败 → PlatformError"""
    client = PlatformClient("http://localhost:33412", token="jwt")
    payload = {"code": 400, "message": "设备 Key 只能含字母数字下划线短横线冒号", "data": None}
    with patch.object(client._client, "request", new=AsyncMock(return_value=_mock_response(200, payload))):
        with pytest.raises(PlatformError, match="设备 Key"):
            await client.create_device("1", "bad key with space", "name")
    await client.close()


@pytest.mark.asyncio
async def test_create_device_empty_params() -> None:
    client = PlatformClient("http://localhost:33412", token="jwt")
    with pytest.raises(PlatformError, match="不能为空"):
        await client.create_device("", "dev_001", "name")
    with pytest.raises(PlatformError, match="不能为空"):
        await client.create_device("1", "", "name")
    with pytest.raises(PlatformError, match="不能为空"):
        await client.create_device("1", "dev_001", "")
    await client.close()


@pytest.mark.asyncio
async def test_create_device_requires_login() -> None:
    client = PlatformClient("http://localhost:33412")
    with pytest.raises(PlatformAuthError, match="未登录"):
        await client.create_device("1", "dev_001", "name")
    await client.close()


# ============================================================
# Device 数据类
# ============================================================
def test_device_from_api_masked() -> None:
    """脱敏字段识别"""
    d = Device.from_api({
        "id": "100",
        "deviceKey": "dev_001",
        "deviceSecret": "abcd****1234",   # 含 **** 是脱敏
    })
    assert d.is_secret_masked is True


def test_device_from_api_full() -> None:
    """明文字段识别"""
    d = Device.from_api({
        "id": "100",
        "deviceKey": "dev_001",
        "deviceSecret": "eb550ffc65d3413680e680d23b5a691c",  # 32 位无 ****
    }, masked=False)
    assert d.is_secret_masked is False