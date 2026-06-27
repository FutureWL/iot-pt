"""从平台导入物模型对话框

流程:
  1. 输入平台 URL/账号/租户 → 登录获取 token
  2. 加载产品列表(下拉框)→ 选中
  3. 选中产品 → 自动加载该产品下的设备列表
  4. 选中设备 → 自动拉取明文密钥
  5. 点击「应用」 → 触发 thing_model_imported 信号

返回:
  通过 get_result() 方法获取 ImportResult 对象
  (含 product_key + device_key + device_secret + ConversionResult + thing_model_json)
"""
from __future__ import annotations

import asyncio
import re
from dataclasses import dataclass
from typing import Any

from loguru import logger
from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QLineEdit,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from ...api.platform_client import (
    Device,
    PlatformAuthError,
    PlatformClient,
    PlatformError,
    PlatformNetworkError,
    Product,
)
from ...api.thing_model_converter import ConversionResult, convert_thing_model


@dataclass
class ImportResult:
    """用户点击「应用」后返回的结果"""
    product: Product
    product_key: str           # 已自动填到 DeviceConfig.product_key
    device: Device | None = None   # 完整 Device 对象(含 id)
    device_key: str = ""       # 已自动填到 DeviceConfig.device_key
    device_secret: str = ""    # 已自动填到 DeviceConfig.device_secret(明文)
    device_name: str = ""      # 仅作参考
    thing_model_json: str = "" # 物模型 JSON 字符串(供 UI 展示)
    conversion: ConversionResult | None = None  # 转换后的 properties/events/services


class PlatformImportDialog(QDialog):
    """平台物模型导入对话框"""

    # 自定义信号(用于异步通知 UI 状态变化)
    status_changed = Signal(str, bool)   # (message, is_error)

    def __init__(
        self,
        *,
        base_url: str = "http://localhost:33412",
        username: str = "admin",
        tenant_code: str = "default",
        token: str = "",
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self.setWindowTitle("从平台导入物模型")
        self.resize(640, 600)

        self._client: PlatformClient | None = None
        self._products: list[Product] = []
        self._devices: list[Device] = []
        self._selected_product: Product | None = None
        self._selected_device: Device | None = None
        self._full_device_secret: str = ""  # 选中设备后拉到的明文密钥
        self._result: ImportResult | None = None
        self._loop: asyncio.AbstractEventLoop | None = None

        self._build_ui()
        # 预填上次登录信息
        self.url_edit.setText(base_url)
        self.user_edit.setText(username)
        self.tenant_edit.setText(tenant_code)
        self._set_status(f"未登录{'(已有 token)' if token else ''}", False)

    # --------------------- UI ---------------------
    def _build_ui(self) -> None:
        root = QVBoxLayout(self)

        # ---------- 1. 登录区 ----------
        login_box = QGroupBox("1. 登录平台")
        form = QFormLayout(login_box)

        self.url_edit = QLineEdit("http://localhost:33412")
        self.url_edit.setPlaceholderText("后端 API 地址,如 http://localhost:33412")
        form.addRow("平台 URL:", self.url_edit)

        self.user_edit = QLineEdit("admin")
        form.addRow("用户名:", self.user_edit)

        self.pass_edit = QLineEdit()
        self.pass_edit.setEchoMode(QLineEdit.EchoMode.Password)
        self.pass_edit.setPlaceholderText("admin 默认 123456")
        form.addRow("密码:", self.pass_edit)

        self.tenant_edit = QLineEdit("default")
        form.addRow("租户编码:", self.tenant_edit)

        btn_row = QHBoxLayout()
        self.login_btn = QPushButton("🔐 登录")
        self.login_btn.clicked.connect(self._on_login_clicked)
        btn_row.addWidget(self.login_btn)
        btn_row.addStretch()
        self.status_label = QLabel("未登录")
        self.status_label.setStyleSheet("color: #909399;")
        btn_row.addWidget(self.status_label)
        form.addRow(btn_row)

        root.addWidget(login_box)

        # ---------- 2. 选择产品 ----------
        product_box = QGroupBox("2. 选择产品")
        p_form = QFormLayout(product_box)
        self.product_combo = QComboBox()
        self.product_combo.setEnabled(False)
        self.product_combo.currentIndexChanged.connect(self._on_product_changed)
        p_form.addRow("产品:", self.product_combo)

        self.refresh_btn = QPushButton("🔄 加载产品列表")
        self.refresh_btn.setEnabled(False)
        self.refresh_btn.clicked.connect(self._on_load_products_clicked)
        p_form.addRow("", self.refresh_btn)

        root.addWidget(product_box)

        # ---------- 3. 选择设备 ----------
        device_box = QGroupBox("3. 选择设备 (自动拉取密钥)")
        d_form = QFormLayout(device_box)

        self.device_combo = QComboBox()
        self.device_combo.setEnabled(False)
        self.device_combo.currentIndexChanged.connect(self._on_device_changed)
        d_form.addRow("设备:", self.device_combo)

        dev_btn_row = QHBoxLayout()
        self.refresh_dev_btn = QPushButton("🔄 加载设备列表")
        self.refresh_dev_btn.setEnabled(False)
        self.refresh_dev_btn.clicked.connect(self._on_load_devices_clicked)
        dev_btn_row.addWidget(self.refresh_dev_btn)

        self.new_dev_btn = QPushButton("➕ 新建设备")
        self.new_dev_btn.setEnabled(False)
        self.new_dev_btn.clicked.connect(self._on_create_device_clicked)
        dev_btn_row.addWidget(self.new_dev_btn)
        d_form.addRow("", dev_btn_row)

        # 密钥显示
        self.secret_label = QLabel("未拉取")
        self.secret_label.setStyleSheet(
            "font-family: monospace; color: #c0c4cc; padding: 4px;"
        )
        self.secret_label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        d_form.addRow("设备密钥:", self.secret_label)

        # 状态指示
        self.dev_status_label = QLabel("请先选择产品")
        self.dev_status_label.setStyleSheet("color: #909399; font-size: 12px;")
        d_form.addRow("", self.dev_status_label)

        root.addWidget(device_box)

        # ---------- 4. 预览 ----------
        preview_box = QGroupBox("4. 预览物模型")
        pv_layout = QVBoxLayout(preview_box)
        self.preview_text = QTextEdit()
        self.preview_text.setReadOnly(True)
        self.preview_text.setPlaceholderText("选择产品后,这里显示属性/事件/服务列表预览")
        pv_layout.addWidget(self.preview_text)

        root.addWidget(preview_box)

        # ---------- 进度条 ----------
        self.progress = QProgressBar()
        self.progress.setRange(0, 0)
        self.progress.setVisible(False)
        root.addWidget(self.progress)

        # ---------- 4. 按钮 ----------
        button_box = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        button_box.button(QDialogButtonBox.StandardButton.Ok).setText("✅ 应用并关闭")
        button_box.button(QDialogButtonBox.StandardButton.Ok).setEnabled(False)
        button_box.accepted.connect(self._on_accept)
        button_box.rejected.connect(self._on_reject)
        root.addWidget(button_box)

        self.ok_btn = button_box.button(QDialogButtonBox.StandardButton.Ok)

    # --------------------- 异步辅助 ---------------------
    def _get_loop(self) -> asyncio.AbstractEventLoop:
        """获取 qasync 桥接的事件循环(若不在 qasync 中则用 asyncio.new_event_loop)"""
        if self._loop and not self._loop.is_closed():
            return self._loop
        try:
            # qasync 会把 loop 装到 qApp 上
            from qasync import QApplication
            app = QApplication.instance()
            self._loop = getattr(app, "_qasync_loop", None) or asyncio.get_event_loop()
        except RuntimeError:
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
        return self._loop

    def _run_async(self, coro: Any) -> Any:
        """在 qasync 事件循环中跑 coroutine,阻塞到结果"""
        loop = self._get_loop()
        if loop.is_running():
            # qasync 主循环正在跑,用 run_until_complete 会卡死
            # 这里用 asyncio.run_coroutine_threadsafe 提交并等待 future
            import concurrent.futures
            future = asyncio.run_coroutine_threadsafe(coro, loop)
            return future.result(timeout=15)
        else:
            return loop.run_until_complete(coro)

    # --------------------- 槽函数 ---------------------
    def _set_status(self, msg: str, is_error: bool = False) -> None:
        color = "#f56c6c" if is_error else "#67c23a"
        self.status_label.setText(msg)
        self.status_label.setStyleSheet(f"color: {color};")
        logger.info("[platform-import] {}", msg)

    def _set_busy(self, busy: bool) -> None:
        self.progress.setVisible(busy)
        self.login_btn.setEnabled(not busy)
        self.refresh_btn.setEnabled(not busy and self._client is not None)
        self.refresh_dev_btn.setEnabled(not busy and self._selected_product is not None)
        self.new_dev_btn.setEnabled(not busy and self._selected_product is not None)

    def _on_login_clicked(self) -> None:
        url = self.url_edit.text().strip()
        user = self.user_edit.text().strip()
        pwd = self.pass_edit.text()           # 不 strip,密码可能有空格
        tenant = self.tenant_edit.text().strip()

        if not (url and user and tenant):
            self._set_status("URL/用户名/租户编码不能为空", is_error=True)
            return

        self._set_busy(True)
        self._set_status("登录中...")
        try:
            async def _do_login() -> str:
                client = PlatformClient(base_url=url)
                try:
                    result = await client.login(user, pwd, tenant)
                    self._client = client
                    return result.token
                except Exception:
                    await client.close()
                    raise

            token = self._run_async(_do_login())
            self._set_status(f"✓ 已登录 ({self.user_edit.text()})")
            self.refresh_btn.setEnabled(True)
            self.refresh_dev_btn.setEnabled(self._selected_product is not None)
            self.new_dev_btn.setEnabled(self._selected_product is not None)
            # 登录成功后自动加载产品列表,体验更顺
            self._on_load_products_clicked()
        except PlatformAuthError as e:
            self._set_status(f"认证失败: {e}", is_error=True)
        except PlatformNetworkError as e:
            self._set_status(f"网络错误: {e}", is_error=True)
        except PlatformError as e:
            self._set_status(f"错误: {e}", is_error=True)
        except Exception as e:
            self._set_status(f"未预期错误: {e}", is_error=True)
            logger.exception("登录失败")
        finally:
            self._set_busy(False)

    def _on_load_products_clicked(self) -> None:
        if not self._client:
            self._set_status("请先登录", is_error=True)
            return

        self._set_busy(True)
        self._set_status("加载产品列表...")
        try:
            async def _do() -> list[Product]:
                assert self._client is not None
                return await self._client.list_products()

            products = self._run_async(_do())
            self._products = products

            # 更新下拉框
            self.product_combo.clear()
            for p in products:
                label = f"{p.product_name}  ({p.product_key})"
                self.product_combo.addItem(label, p)
            self.product_combo.setEnabled(True)
            self._set_status(f"✓ 已加载 {len(products)} 个产品")

            # 自动选中上次选过的
            last_id = self._last_product_id
            if last_id:
                for i, p in enumerate(products):
                    if p.id == last_id:
                        self.product_combo.setCurrentIndex(i)
                        break
        except PlatformAuthError as e:
            self._set_status(f"token 已过期: {e}", is_error=True)
        except PlatformError as e:
            self._set_status(f"加载失败: {e}", is_error=True)
        except Exception as e:
            logger.exception("加载产品列表失败")
            self._set_status(f"未预期错误: {e}", is_error=True)
        finally:
            self._set_busy(False)

    def _on_product_changed(self, idx: int) -> None:
        if idx < 0 or idx >= len(self._products):
            return
        product = self._products[idx]
        self._selected_product = product
        self._set_status(f"选中产品: {product.product_name}")
        # 清空旧设备
        self._devices = []
        self._selected_device = None
        self._full_device_secret = ""
        self.device_combo.clear()
        self.device_combo.setEnabled(False)
        self.secret_label.setText("未拉取")
        self.dev_status_label.setText("加载设备中...")
        # 启用设备操作按钮
        self.refresh_dev_btn.setEnabled(True)
        self.new_dev_btn.setEnabled(True)
        self._render_preview(product)
        self.ok_btn.setEnabled(False)   # 需先选设备才能应用
        # 自动加载设备列表
        self._on_load_devices_clicked()

    def _on_load_devices_clicked(self) -> None:
        """加载当前产品下的设备列表(密钥脱敏)"""
        if not self._client or not self._selected_product:
            return
        self._set_busy(True)
        self.dev_status_label.setText("加载设备列表...")
        try:
            async def _do() -> list[Device]:
                assert self._client is not None
                assert self._selected_product is not None
                return await self._client.list_devices_by_product(
                    self._selected_product.id
                )

            devices = self._run_async(_do())
            self._devices = devices

            self.device_combo.clear()
            for d in devices:
                status_text = {0: "禁用", 1: "在线"}.get(d.status, "?")
                label = f"{d.device_name or d.device_key}  [{status_text}]"
                self.device_combo.addItem(label, d)
            self.device_combo.setEnabled(True)
            self.dev_status_label.setText(f"✓ {len(devices)} 台设备")

            if not devices:
                self.dev_status_label.setText("该产品下暂无设备,可点 ➕ 新建设备")
        except PlatformError as e:
            self.dev_status_label.setText(f"✗ {e}")
        except Exception as e:
            logger.exception("加载设备列表失败")
            self.dev_status_label.setText(f"✗ {e}")
        finally:
            self._set_busy(False)

    def _on_device_changed(self, idx: int) -> None:
        """选中设备 → 拉取明文密钥 + 显示"""
        if idx < 0 or idx >= len(self._devices):
            return
        device = self._devices[idx]
        self._selected_device = device

        if not self._client:
            return

        # 拉取明文密钥
        try:
            async def _do() -> Device:
                assert self._client is not None
                return await self._client.get_device_full(device.id)

            full = self._run_async(_do())
            self._full_device_secret = full.device_secret
            self.secret_label.setText(self._mask_secret(full.device_secret))
            self.secret_label.setStyleSheet(
                "font-family: monospace; color: #67c23a; padding: 4px;"
            )
            self.dev_status_label.setText(
                f"✓ {full.device_key}  密钥已拉取(明文长度 {len(full.device_secret)})"
            )
            self.ok_btn.setEnabled(True)
        except PlatformError as e:
            self._full_device_secret = ""
            self.secret_label.setText("拉取失败")
            self.secret_label.setStyleSheet(
                "font-family: monospace; color: #f56c6c; padding: 4px;"
            )
            self.dev_status_label.setText(f"✗ {e}")
            self.ok_btn.setEnabled(False)
        except Exception as e:
            logger.exception("拉取设备详情失败")
            self.dev_status_label.setText(f"✗ {e}")
            self.ok_btn.setEnabled(False)

    def _on_create_device_clicked(self) -> None:
        """在当前产品下新建设备"""
        if not self._client or not self._selected_product:
            return
        product = self._selected_product

        # 弹窗输入 device_key 和 name
        device_key, ok = QInputDialog.getText(
            self, "新建设备",
            f"在产品 «{product.product_name}» 下创建新设备。\n\n设备 Key(只能含字母数字下划线短横线):",
            text=f"dev_{int(asyncio.get_event_loop().time() * 1000) % 100000:05d}",
        )
        if not ok or not device_key.strip():
            return
        device_key = device_key.strip()
        if not re.match(r"^[A-Za-z0-9_:-]{2,64}$", device_key):
            QMessageBox.warning(self, "设备 Key 不合法",
                                "只能含字母、数字、下划线、短横线、冒号,长度 2-64")
            return

        device_name, ok = QInputDialog.getText(
            self, "设备名", "设备名称(显示用):", text=device_key,
        )
        if not ok or not device_name.strip():
            return
        device_name = device_name.strip()

        self._set_busy(True)
        try:
            async def _do() -> Device:
                assert self._client is not None
                assert self._selected_product is not None
                return await self._client.create_device(
                    self._selected_product.id, device_key, device_name
                )

            new_dev = self._run_async(_do())
            self.dev_status_label.setText(f"✓ 已创建设备 {new_dev.device_key}")
            # 刷新列表
            self._on_load_devices_clicked()
            # 选中新建的设备
            for i, d in enumerate(self._devices):
                if d.id == new_dev.id:
                    self.device_combo.setCurrentIndex(i)
                    break
            QMessageBox.information(
                self, "设备已创建",
                f"设备 Key: {new_dev.device_key}\n"
                f"设备密钥: {new_dev.device_secret}\n\n"
                f"密钥已同步到模拟器配置。\n请妥善保存,这是唯一一次明文显示。",
            )
        except PlatformError as e:
            QMessageBox.warning(self, "创建失败", str(e))
            self.dev_status_label.setText(f"✗ 创建失败: {e}")
        except Exception as e:
            logger.exception("新建设备失败")
            QMessageBox.warning(self, "创建失败", str(e))
        finally:
            self._set_busy(False)

    @staticmethod
    def _mask_secret(secret: str) -> str:
        """密钥脱敏显示: 保留前 4 后 4 位,中间用 ****"""
        if not secret:
            return ""
        if len(secret) <= 8:
            return "****"
        return f"{secret[:4]}****{secret[-4:]}  (明文已复制到模拟器)"

    def _render_preview(self, product: Product) -> None:
        """渲染预览(纯展示,真正加载在 _on_accept 时)"""
        if not self._client:
            self.preview_text.setPlainText("请先登录")
            return

        async def _do() -> tuple[ConversionResult, str]:
            assert self._client is not None
            tm = await self._client.get_thing_model(product.id)
            conv = convert_thing_model(tm)
            # 物模型 JSON 字符串(by_alias 保留平台字段名)
            import json
            return conv, json.dumps(tm, ensure_ascii=False, indent=2)

        try:
            self._set_busy(True)
            conversion, tm_json = self._run_async(_do())

            # 保存到 instance(等用户点 OK 时取)
            self._pending_conversion = conversion
            self._pending_tm_json = tm_json

            # 渲染预览
            lines = [
                f"产品: {product.product_name} (Key={product.product_key})",
                f"分类: {product.category or '-'}    网络: {product.net_type or '-'}",
                "",
                f"📊 属性 ({len(conversion.properties)} 个):",
            ]
            for p in conversion.properties[:10]:
                rng = (
                    f" [{p.min} ~ {p.max}]"
                    if (p.min is not None and p.max is not None)
                    else ""
                )
                lines.append(f"  • {p.identifier} ({p.type}){rng}  策略={p.strategy.value}")
            if len(conversion.properties) > 10:
                lines.append(f"  ... 还有 {len(conversion.properties) - 10} 个")

            lines.append("")
            lines.append(f"⚡ 事件 ({len(conversion.events)} 个):")
            for e in conversion.events:
                lines.append(f"  • {e.identifier} ({e.type})")

            lines.append("")
            lines.append(f"🔧 服务 ({len(conversion.services)} 个):")
            for s in conversion.services:
                lines.append(f"  • {s.identifier}")

            if conversion.warnings:
                lines.append("")
                lines.append(f"⚠ 转换告警 ({len(conversion.warnings)} 条):")
                for w in conversion.warnings:
                    lines.append(f"  - {w}")

            lines.append("")
            lines.append("点「应用并关闭」将此物模型导入当前模拟器配置。")
            self.preview_text.setPlainText("\n".join(lines))
            self._set_status(f"✓ 预览就绪,可应用")
        except Exception as e:
            logger.exception("预览失败")
            self.preview_text.setPlainText(f"加载物模型失败:\n{e}")
            self.ok_btn.setEnabled(False)

    # --------------------- 接受/拒绝 ---------------------
    def _on_accept(self) -> None:
        """用户点「应用并关闭」"""
        if not self._selected_product:
            QMessageBox.warning(self, "提示", "请先选择产品")
            return
        if not hasattr(self, "_pending_conversion"):
            QMessageBox.warning(self, "提示", "请先预览物模型")
            return
        if not self._selected_device:
            QMessageBox.warning(self, "提示", "请先选择设备(或新建设备)")
            return
        if not self._full_device_secret:
            QMessageBox.warning(self, "提示", "设备密钥未拉取,请重新选中设备")
            return

        self._result = ImportResult(
            product=self._selected_product,
            product_key=self._selected_product.product_key,
            device=self._selected_device,
            device_key=self._selected_device.device_key,
            device_secret=self._full_device_secret,
            device_name=self._selected_device.device_name,
            thing_model_json=self._pending_tm_json,
            conversion=self._pending_conversion,
        )
        self.accept()

    def _on_reject(self) -> None:
        """用户点取消"""
        if self._client:
            try:
                self._run_async(self._client.close())
            except Exception:
                pass
        self.reject()

    # --------------------- 对外 API ---------------------
    def get_result(self) -> ImportResult | None:
        """返回 ImportResult(QDialog.exec() == Accepted 后才有值)"""
        return self._result

    @property
    def last_product_id(self) -> str:
        return self._selected_product.id if self._selected_product else ""

    def get_auth(self) -> tuple[str, str, str, str]:
        """返回 (base_url, username, tenant_code, token) 供 main_window 持久化"""
        token = self._client.token if self._client else ""
        return (
            self.url_edit.text().strip(),
            self.user_edit.text().strip(),
            self.tenant_edit.text().strip(),
            token,
        )


__all__ = ["PlatformImportDialog", "ImportResult"]