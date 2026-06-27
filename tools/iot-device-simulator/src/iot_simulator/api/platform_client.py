"""平台 API 客户端 - 与 iot-pt 后端交互

提供:
  - 登录获取 JWT
  - 拉取产品列表
  - 拉取单个产品的物模型 JSON

参考后端实现:
  iot-pt/backend/src/main/java/com/iot/platform/product/controller/IotProductController.java
  iot-pt/backend/src/main/java/com/iot/platform/auth/controller/AuthController.java
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any

import httpx
from loguru import logger


# ============================================================
# 数据类
# ============================================================
@dataclass(frozen=True)
class Product:
    """平台产品(简化视图,UI 下拉用)"""
    id: str                # 平台内部 ID(字符串形式的 Long)
    product_key: str       # 产品 Key(全局唯一)
    product_name: str      # 显示名
    category: str = ""
    net_type: str = ""     # MQTT / TCP / ...
    description: str = ""

    @classmethod
    def from_api(cls, data: dict[str, Any]) -> "Product":
        return cls(
            id=str(data.get("id", "")),
            product_key=data.get("productKey", ""),
            product_name=data.get("productName", ""),
            category=data.get("category", "") or "",
            net_type=data.get("netType", "") or "",
            description=data.get("description", "") or "",
        )


@dataclass(frozen=True)
class Device:
    """平台设备(UI 下拉用)

    注意: 默认从 /iot/device/page 拉取时 secret 是脱敏的(eb55****691c)
    如需明文,调 PlatformClient.get_device_full()
    """
    id: str
    product_id: str = ""
    product_key: str = ""
    device_key: str = ""
    device_name: str = ""
    device_secret: str = ""      # 脱敏或明文取决于调哪个接口
    protocol: str = ""           # MQTT / TCP
    status: int = 0              # 0=禁用/离线,1=启用/在线
    is_secret_masked: bool = True  # secret 是否被脱敏

    @classmethod
    def from_api(cls, data: dict[str, Any], masked: bool = True) -> "Device":
        secret = data.get("deviceSecret", "") or ""
        return cls(
            id=str(data.get("id", "")),
            product_id=str(data.get("productId", "")),
            product_key=data.get("productKey", ""),
            device_key=data.get("deviceKey", ""),
            device_name=data.get("deviceName", ""),
            device_secret=secret,
            protocol=data.get("protocol", "") or "",
            status=int(data.get("status", 0) or 0),
            is_secret_masked=masked,
        )


@dataclass
class LoginResult:
    """登录结果"""
    token: str
    user_id: str = ""
    username: str = ""
    tenant_id: str = ""
    tenant_code: str = ""
    expires_at: str = ""   # ISO 时间,留空表示未知


# ============================================================
# 异常
# ============================================================
class PlatformError(Exception):
    """平台 API 通用错误"""


class PlatformAuthError(PlatformError):
    """认证失败(401/403,或登录返回非 200)"""


class PlatformNotFoundError(PlatformError):
    """资源不存在(404)"""


class PlatformNetworkError(PlatformError):
    """网络错误(连接失败、超时等)"""


# ============================================================
# 客户端
# ============================================================
class PlatformClient:
    """iot-pt 平台 HTTP 客户端(异步)

    用法:
        async with PlatformClient("http://localhost:33412") as client:
            await client.login("admin", "123456", "default")
            products = await client.list_products()
            tm = await client.get_thing_model(products[0].id)
    """

    def __init__(
        self,
        base_url: str,
        token: str | None = None,
        timeout: float = 10.0,
    ) -> None:
        # 自动去掉末尾斜杠,统一拼接
        self.base_url = base_url.rstrip("/")
        self.token = token
        self._client = httpx.AsyncClient(
            base_url=self.base_url,
            timeout=timeout,
            headers={"User-Agent": "iot-device-simulator/0.1"},
        )

    async def close(self) -> None:
        await self._client.aclose()

    async def __aenter__(self) -> "PlatformClient":
        return self

    async def __aexit__(self, *exc: Any) -> None:
        await self.close()

    # --------------------- 内部辅助 ---------------------
    def _auth_headers(self) -> dict[str, str]:
        if not self.token:
            return {}
        return {"Authorization": f"Bearer {self.token}"}

    @staticmethod
    def _unwrap(resp: httpx.Response) -> dict[str, Any]:
        """解包后端统一 R<T> 响应 {code, message, data, timestamp}"""
        if resp.status_code == 401 or resp.status_code == 403:
            raise PlatformAuthError(f"认证失败: HTTP {resp.status_code}")
        if resp.status_code == 404:
            raise PlatformNotFoundError(f"资源不存在: HTTP {resp.status_code}")
        try:
            payload = resp.json()
        except Exception as e:
            raise PlatformError(f"响应不是 JSON: {e}; body={resp.text[:200]!r}") from e

        if not isinstance(payload, dict):
            raise PlatformError(f"响应格式异常: {payload!r}")
        code = payload.get("code")
        if code != 200:
            msg = payload.get("message") or f"HTTP {resp.status_code}"
            if "租户" in msg or "用户" in msg or "密码" in msg or "登录" in msg:
                raise PlatformAuthError(f"{msg} (code={code})")
            raise PlatformError(f"{msg} (code={code})")
        return payload

    async def _request(self, method: str, path: str, **kw: Any) -> dict[str, Any]:
        try:
            resp = await self._client.request(
                method, path, headers={**self._auth_headers(), **kw.pop("headers", {})},
                **kw,
            )
        except httpx.ConnectError as e:
            raise PlatformNetworkError(f"无法连接 {self.base_url}: {e}") from e
        except httpx.TimeoutException as e:
            raise PlatformNetworkError(f"请求超时: {e}") from e
        except httpx.HTTPError as e:
            raise PlatformNetworkError(f"HTTP 错误: {e}") from e
        return self._unwrap(resp)

    # --------------------- 对外 API ---------------------
    async def login(self, username: str, password: str, tenant_code: str) -> LoginResult:
        """登录获取 JWT token

        请求: POST /api/auth/login
        响应: {code, message, data: {token, userId, username, tenantId, tenantCode, ...}}
        """
        path = "/api/auth/login"
        # 后端要求 username/password/tenantCode 三个字段
        payload = {
            "username": username,
            "password": password,
            "tenantCode": tenant_code,
        }
        logger.info("[platform] 登录 user={} tenant={}", username, tenant_code)
        data = await self._request("POST", path, json=payload)
        body = data.get("data") or {}
        token = body.get("token")
        if not token:
            raise PlatformAuthError(f"登录响应无 token: {body}")
        self.token = token
        return LoginResult(
            token=token,
            user_id=str(body.get("userId", "")),
            username=body.get("username", ""),
            tenant_id=str(body.get("tenantId", "")),
            tenant_code=body.get("tenantCode", ""),
        )

    async def list_products(self) -> list[Product]:
        """拉取所有产品(下拉框用)

        请求: GET /api/iot/product/all
        响应: {code, data: [{id, productKey, productName, ...}, ...]}
        """
        path = "/api/iot/product/all"
        if not self.token:
            raise PlatformAuthError("未登录,请先调用 login()")
        logger.info("[platform] 拉取产品列表")
        data = await self._request("GET", path)
        items = data.get("data") or []
        return [Product.from_api(it) for it in items if isinstance(it, dict)]

    async def get_thing_model(self, product_id: str) -> dict[str, Any]:
        """拉取指定产品的物模型(JSON 字符串 → dict)

        请求: GET /api/iot/product/{id}
        响应: data.thingModel 是 JSON 字符串,需要二次 parse
        """
        if not self.token:
            raise PlatformAuthError("未登录,请先调用 login()")
        if not product_id:
            raise PlatformError("product_id 不能为空")
        path = f"/api/iot/product/{product_id}"
        logger.info("[platform] 拉取物模型 productId={}", product_id)
        data = await self._request("GET", path)
        body = data.get("data") or {}
        tm_raw = body.get("thingModel")
        if not tm_raw:
            # 平台允许物模型为空(刚创建的产品)
            logger.warning("[platform] 产品 {} 物模型为空", product_id)
            return {}
        if isinstance(tm_raw, dict):
            return tm_raw
        if isinstance(tm_raw, str):
            try:
                return json.loads(tm_raw)
            except json.JSONDecodeError as e:
                raise PlatformError(
                    f"产品 {product_id} 物模型 JSON 解析失败: {e}"
                ) from e
        raise PlatformError(f"物模型字段类型异常: {type(tm_raw)}")

    async def get_default_thing_model(self) -> dict[str, Any]:
        """拉取平台默认物模型模板(给"新建产品"用)

        请求: GET /api/iot/product/thing-model/default
        响应: data 是 JSON 字符串
        """
        if not self.token:
            raise PlatformAuthError("未登录,请先调用 login()")
        path = "/api/iot/product/thing-model/default"
        logger.info("[platform] 拉取默认物模型模板")
        data = await self._request("GET", path)
        tm = data.get("data")
        if isinstance(tm, str):
            return json.loads(tm) if tm.strip() else {}
        if isinstance(tm, dict):
            return tm
        return {}

    # --------------------- 设备 ---------------------
    async def list_devices_by_product(
        self,
        product_id: str,
        page_num: int = 1,
        page_size: int = 100,
    ) -> list[Device]:
        """拉取指定产品下的设备列表(密钥脱敏)

        请求: GET /api/iot/device/page?productId={id}&pageNum=1&pageSize=100
        响应: data = {records: [...], total, size, current, pages}
        注意: 这里返回的 deviceSecret 是脱敏的(如 eb55****691c)
        如需明文,在选中后调 get_device_full()
        """
        if not self.token:
            raise PlatformAuthError("未登录,请先调用 login()")
        if not product_id:
            raise PlatformError("product_id 不能为空")
        path = "/api/iot/device/page"
        logger.info("[platform] 拉取设备列表 productId={}", product_id)
        params = {
            "productId": product_id,
            "pageNum": page_num,
            "pageSize": page_size,
        }
        data = await self._request("GET", path, params=params)
        page = data.get("data") or {}
        records = page.get("records") or []
        return [Device.from_api(r, masked=True) for r in records if isinstance(r, dict)]

    async def get_device_full(self, device_id: str) -> Device:
        """拉取设备详情(含明文密钥)

        请求: GET /api/iot/device/{id}/full
        响应: data.deviceSecret 是 32 位十六进制明文

        注意: 返回明文密钥会记录到日志,调用前请评估安全风险。
        生产环境建议在调用层用 mask_secret() 避免日志泄漏。
        """
        if not self.token:
            raise PlatformAuthError("未登录,请先调用 login()")
        if not device_id:
            raise PlatformError("device_id 不能为空")
        path = f"/api/iot/device/{device_id}/full"
        # 这里不打 logger,避免明文密钥进入日志
        data = await self._request("GET", path)
        body = data.get("data") or {}
        return Device.from_api(body, masked=False)

    async def create_device(
        self,
        product_id: str,
        device_key: str,
        device_name: str,
    ) -> Device:
        """在平台上新建设备

        请求: POST /api/iot/device
        请求体: {productId, deviceKey, deviceName}  (deviceSecret 留空,后端自动生成)
        响应: data 是完整 Device(含明文 deviceSecret)

        注意: 需要平台「设备创建」权限(管理员/租户管理员)。
        返回的 Device.secret 是明文,**仅显示一次**,需立即保存。
        """
        if not self.token:
            raise PlatformAuthError("未登录,请先调用 login()")
        if not product_id or not device_key or not device_name:
            raise PlatformError("product_id/device_key/device_name 不能为空")
        path = "/api/iot/device"
        payload = {
            "productId": int(product_id) if product_id.isdigit() else product_id,
            "deviceKey": device_key,
            "deviceName": device_name,
        }
        # 不打印 device_key,避免泄漏
        logger.info("[platform] 新建设备 productId={}", product_id)
        data = await self._request("POST", path, json=payload)
        body = data.get("data") or {}
        if not body or "deviceSecret" not in body:
            raise PlatformError(f"新建设备响应异常: {body}")
        return Device.from_api(body, masked=False)


__all__ = [
    "PlatformClient",
    "Product",
    "Device",
    "LoginResult",
    "PlatformError",
    "PlatformAuthError",
    "PlatformNotFoundError",
    "PlatformNetworkError",
]