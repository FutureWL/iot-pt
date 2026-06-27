"""主窗口 - 5 个面板 + 底部控制栏"""
from __future__ import annotations

from pathlib import Path
from typing import Optional

from loguru import logger
from PySide6.QtCore import QTimer
from PySide6.QtGui import QAction, QCloseEvent
from PySide6.QtWidgets import (
    QFileDialog,
    QHBoxLayout,
    QLabel,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSplitter,
    QStatusBar,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from ..core.config import DeviceConfig
from ..core.simulator import DeviceSimulator
from ..utils.storage import load_yaml, save_yaml
from .dialogs.thing_model_dialog import ThingModelDialog
from .widgets.connection_panel import ConnectionPanel
from .widgets.event_panel import EventPanel
from .widgets.log_panel import LogPanel
from .widgets.property_panel import PropertyPanel
from .widgets.service_panel import ServicePanel


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("IoT 设备模拟器")
        self.resize(1280, 800)

        self.simulator = DeviceSimulator(self)
        self._current_cfg: DeviceConfig = self.simulator.cfg or self._build_default_cfg()
        self._current_path: Optional[Path] = None
        self._build_ui()
        self._wire()
        self._wire_simulator()
        self._start_tick()

    def _build_default_cfg(self) -> DeviceConfig:
        return DeviceConfig(
            product_key="pk_demo",
            device_key="dev_001",
            device_secret="",
        )

    def _build_ui(self) -> None:
        # 中央: 上半部分(配置) + 下半部分(日志)
        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(8, 8, 8, 8)

        splitter = QSplitter()
        from PySide6.QtCore import Qt
        splitter.setOrientation(Qt.Orientation.Vertical)
        root.addWidget(splitter)

        # ===== 上半:连接 + 物模型导入 + 配置区 =====
        top = QWidget()
        top_layout = QVBoxLayout(top)
        top_layout.setContentsMargins(0, 0, 0, 0)

        # 连接 + 物模型导入按钮
        top_row = QHBoxLayout()
        self.conn_panel = ConnectionPanel()
        self.conn_panel.setMinimumWidth(380)
        self.conn_panel.setMaximumWidth(420)
        top_row.addWidget(self.conn_panel, 0)

        # 物模型导入区
        tm_widget = QWidget()
        tm_layout = QVBoxLayout(tm_widget)
        tm_layout.setContentsMargins(0, 0, 0, 0)
        from PySide6.QtWidgets import QGroupBox, QFormLayout, QPlainTextEdit
        tm_group = QGroupBox("物模型 (粘贴 JSON / 留空手填)")
        tm_form = QFormLayout(tm_group)
        self.tm_text = QPlainTextEdit()
        self.tm_text.setPlaceholderText('{"properties":[], "events":[], "services":[]}')
        self.tm_text.setMaximumHeight(120)
        tm_form.addRow(self.tm_text)
        self.btn_import_tm = QPushButton("📋 从对话框导入...")
        self.btn_import_tm.setMaximumWidth(200)
        tm_layout.addWidget(tm_group)
        tm_layout.addWidget(self.btn_import_tm)
        top_row.addWidget(tm_widget, 1)
        top_layout.addLayout(top_row)

        # 物模型应用后:tab 切换
        self.tabs = QTabWidget()
        self.prop_panel = PropertyPanel()
        self.event_panel = EventPanel()
        self.svc_panel = ServicePanel()
        self.tabs.addTab(self.prop_panel, "属性")
        self.tabs.addTab(self.event_panel, "事件")
        self.tabs.addTab(self.svc_panel, "服务")
        top_layout.addWidget(self.tabs, 1)

        # ===== 下半:日志 =====
        self.log_panel = LogPanel()

        splitter.addWidget(top)
        splitter.addWidget(self.log_panel)
        splitter.setStretchFactor(0, 3)
        splitter.setStretchFactor(1, 2)

        # ===== 底部控制栏 =====
        ctrl_row = QHBoxLayout()
        self.btn_start = QPushButton("▶ 开始模拟")
        self.btn_start.setStyleSheet("background:#67C23A; color:white; padding:8px 24px; font-weight:bold;")
        self.btn_stop = QPushButton("⏹ 停止")
        self.btn_stop.setEnabled(False)
        ctrl_row.addWidget(self.btn_start)
        ctrl_row.addWidget(self.btn_stop)
        ctrl_row.addStretch(1)

        self.btn_load = QPushButton("📂 加载配置")
        self.btn_save = QPushButton("💾 保存配置")
        ctrl_row.addWidget(self.btn_load)
        ctrl_row.addWidget(self.btn_save)
        root.addLayout(ctrl_row)

        # ===== 菜单栏 =====
        self._build_menu()
        # ===== 状态栏 =====
        self.status_lbl = QLabel("未连接")
        sb = QStatusBar()
        sb.addPermanentWidget(self.status_lbl)
        self.setStatusBar(sb)

    def _build_menu(self) -> None:
        mb = self.menuBar()
        m_file = mb.addMenu("文件")
        m_file.addAction(QAction("新建", self, triggered=self._on_new))
        m_file.addAction(QAction("打开配置...", self, triggered=self._on_load))
        m_file.addAction(QAction("保存配置", self, triggered=self._on_save))
        m_file.addAction(QAction("另存为...", self, triggered=self._on_save_as))
        m_file.addSeparator()
        m_file.addAction(QAction("退出", self, triggered=self.close))
        m_view = mb.addMenu("视图")
        m_view.addAction(QAction("放大 +", self, triggered=self._on_zoom_in))
        m_view.addAction(QAction("缩小 -", self, triggered=self._on_zoom_out))
        m_view.addAction(QAction("重置 100%", self, triggered=self._on_zoom_reset))
        m_help = mb.addMenu("帮助")
        m_help.addAction(QAction("关于", self, triggered=self._on_about))

    def _wire(self) -> None:
        self.btn_start.clicked.connect(self._on_start)
        self.btn_stop.clicked.connect(self._on_stop)
        self.btn_load.clicked.connect(self._on_load)
        self.btn_save.clicked.connect(self._on_save)
        self.btn_import_tm.clicked.connect(self._on_import_thing_model)
        self.conn_panel.config_changed.connect(self._on_config_changed)
        self.prop_panel.properties_changed.connect(lambda _: self._on_config_changed())
        self.event_panel.events_changed.connect(lambda _: self._on_config_changed())
        self.svc_panel.services_changed.connect(lambda _: self._on_config_changed())
        self.event_panel.fire_manual.connect(self._on_fire_manual)

    def _wire_simulator(self) -> None:
        self.simulator.state_changed.connect(self._on_state)
        self.simulator.log_emitted.connect(self.log_panel.append)
        self.simulator.stats_changed.connect(self._on_stats)

    def _start_tick(self) -> None:
        self._tick_timer = QTimer(self)
        self._tick_timer.setInterval(100)  # 100ms
        self._tick_timer.timeout.connect(self.simulator.tick)
        self._tick_timer.start()

    # ----- 状态 / 日志 -----
    def _on_state(self, state) -> None:
        if state.connected:
            txt = f"● 已连接  {state.protocol}  {state.host}:{state.port}"
            self.status_lbl.setText(txt)
            self.btn_start.setEnabled(False)
            self.btn_stop.setEnabled(True)
        else:
            err = f"  ({state.error})" if state.error else ""
            self.status_lbl.setText(f"○ 未连接{err}")
            self.btn_start.setEnabled(True)
            self.btn_stop.setEnabled(False)

    def _on_stats(self, stats: dict) -> None:
        # 在状态栏右侧追加统计
        self.statusBar().showMessage(
            f"TX 属性: {stats['tx_property']}  |  TX 事件: {stats['tx_event']}  |  RX 下行: {stats['rx_downlink']}",
            0,
        )

    # ----- 按钮 -----
    def _on_start(self) -> None:
        self._collect_config()
        if not self._current_cfg.product_key:
            QMessageBox.warning(self, "缺少配置", "请先填写产品 Key")
            self.conn_panel.product_key.setFocus()
            return
        if not self._current_cfg.device_key:
            QMessageBox.warning(self, "缺少配置", "请先填写设备 Key")
            self.conn_panel.device_key.setFocus()
            return
        if not self._current_cfg.device_secret:
            ret = QMessageBox.question(
                self, "设备密钥为空",
                "设备密钥为空,可能导致鉴权失败。\n是否继续?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            )
            if ret != QMessageBox.StandardButton.Yes:
                return
        self.simulator.configure(self._current_cfg)
        ok = self.simulator.start()
        if not ok:
            QMessageBox.warning(self, "启动失败", "连接失败,请检查配置/网络/服务器")

    def _on_stop(self) -> None:
        self.simulator.stop()

    def _on_fire_manual(self, ident: str) -> None:
        if not self.simulator.is_connected if hasattr(self.simulator, 'is_connected') else not self.simulator._state.connected:
            QMessageBox.information(self, "未连接", "请先连接后再触发事件")
            return
        self.simulator.fire_event_manual(ident)

    def _on_config_changed(self) -> None:
        self._collect_config()

    def _collect_config(self) -> None:
        """从各面板收集配置"""
        cfg = self.conn_panel.get_config()
        cfg.properties = self.prop_panel.get_properties()
        cfg.events = self.event_panel.get_events()
        cfg.services = self.svc_panel.get_services()
        cfg.thing_model_json = self.tm_text.toPlainText().strip()
        self._current_cfg = cfg

    def _on_import_thing_model(self) -> None:
        dlg = ThingModelDialog(self.tm_text.toPlainText(), self)
        if dlg.exec() == ThingModelDialog.Accepted:
            self.tm_text.setPlainText(dlg.text.toPlainText())
            # 自动填充属性
            try:
                self.prop_panel.populate_from_thing_model(
                    dlg.text.toPlainText(),
                    existing=self.prop_panel.get_properties(),
                )
            except Exception as e:
                logger.warning("从物模型填充属性失败: {}", e)

    def _on_new(self) -> None:
        self._current_cfg = self._build_default_cfg()
        self.conn_panel.set_config(self._current_cfg)
        self.prop_panel.set_properties([])
        self.event_panel.set_events([])
        self.svc_panel.set_services([])
        self.tm_text.clear()
        self._current_path = None

    def _on_load(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self, "加载配置", "", "YAML files (*.yaml *.yml);;All files (*)"
        )
        if not path:
            return
        try:
            data = load_yaml(Path(path))
            cfg = DeviceConfig(**data.get("device", {}))
            tm = data.get("thing_model", "")
            self.conn_panel.set_config(cfg)
            self.tm_text.setPlainText(tm)
            self.prop_panel.set_properties(cfg.properties)
            self.event_panel.set_events(cfg.events)
            self.svc_panel.set_services(cfg.services)
            self._current_cfg = cfg
            self._current_path = Path(path)
            self.statusBar().showMessage(f"已加载: {path}", 3000)
        except Exception as e:
            QMessageBox.warning(self, "加载失败", str(e))

    def _on_save(self) -> None:
        if self._current_path is None:
            self._on_save_as()
            return
        self._do_save(self._current_path)

    def _on_save_as(self) -> None:
        path, _ = QFileDialog.getSaveFileName(
            self, "保存配置", "device.yaml", "YAML files (*.yaml *.yml);;All files (*)"
        )
        if not path:
            return
        self._do_save(Path(path))
        self._current_path = Path(path)

    def _do_save(self, path: Path) -> None:
        self._collect_config()
        try:
            data = {
                "device": self._current_cfg.model_dump(
                    exclude={"thing_model_json"}, mode="json"
                ),
                "thing_model": self._current_cfg.thing_model_json,
            }
            save_yaml(data, path)
            self.statusBar().showMessage(f"已保存: {path}", 3000)
        except Exception as e:
            QMessageBox.warning(self, "保存失败", str(e))

    def _on_about(self) -> None:
        QMessageBox.about(
            self,
            "关于",
            "<h3>IoT 设备模拟器</h3>"
            "<p>独立 GUI 程序,模拟 IoT 设备向平台上报数据</p>"
            "<p>支持 MQTT / TCP,物模型驱动属性生成</p>"
            "<p>项目: iot-device-simulator v0.1.0</p>",
        )

    # ----- 视图缩放 -----
    def _on_zoom_in(self) -> None:
        QApplication.instance().font().setPointSize(
            QApplication.instance().font().pointSize() + 1
        )

    def _on_zoom_out(self) -> None:
        font = QApplication.instance().font()
        if font.pointSize() > 6:
            font.setPointSize(font.pointSize() - 1)

    def _on_zoom_reset(self) -> None:
        # 触发“View → Reset” (不实际重起,但重启后可通过环境变量重置)
        QMessageBox.information(self, "提示", "请重新启动应用以应用重置。\n启动时设 QT_SCALE_FACTOR=1 即可。")

    def closeEvent(self, event: QCloseEvent) -> None:
        # 停止模拟器
        try:
            self.simulator.stop()
        except Exception:
            pass
        # 停止 tick
        if hasattr(self, "_tick_timer"):
            self._tick_timer.stop()
        super().closeEvent(event)
