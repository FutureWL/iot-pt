"""连接配置面板 - 产品Key/设备Key/密钥/协议/服务器"""
from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLineEdit,
    QPushButton,
    QSpinBox,
    QVBoxLayout,
    QWidget,
)

from ...core.config import DeviceConfig, MqttConfig, ProtocolType, TcpConfig
from ...model.enums import ProtocolType as PT


class ConnectionPanel(QWidget):
    """连接配置面板"""
    config_changed = Signal(object)   # DeviceConfig

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._build_ui()
        self._wire()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        group = QGroupBox("连接配置")
        root.addWidget(group)
        form = QFormLayout(group)

        # 产品 / 设备 / 密钥
        self.product_key = QLineEdit()
        self.product_key.setPlaceholderText("如: pk_temp_sensor")
        form.addRow("产品 Key:", self.product_key)

        self.device_key = QLineEdit()
        self.device_key.setPlaceholderText("如: dev_001")
        form.addRow("设备 Key:", self.device_key)

        self.device_secret = QLineEdit()
        self.device_secret.setEchoMode(QLineEdit.EchoMode.Password)
        self.device_secret.setPlaceholderText("在平台创建设备时生成")
        form.addRow("设备密钥:", self.device_secret)

        # 协议选择
        self.protocol_combo = QComboBox()
        for p in ProtocolType:
            self.protocol_combo.addItem(p.value, p)
        form.addRow("协议:", self.protocol_combo)

        # MQTT 配置区
        self.mqtt_group = QGroupBox("MQTT")
        mqtt_form = QFormLayout(self.mqtt_group)
        self.mqtt_host = QLineEdit("localhost")
        self.mqtt_port = QSpinBox()
        self.mqtt_port.setRange(1, 65535)
        self.mqtt_port.setValue(1883)
        self.mqtt_client_id = QLineEdit()
        self.mqtt_client_id.setPlaceholderText("空 = simulator-{device_key}")
        self.mqtt_username = QLineEdit()
        self.mqtt_password = QLineEdit()
        self.mqtt_password.setEchoMode(QLineEdit.EchoMode.Password)
        self.mqtt_qos = QSpinBox()
        self.mqtt_qos.setRange(0, 2)
        self.mqtt_qos.setValue(1)
        self.mqtt_use_tls = QCheckBox("使用 TLS")
        mqtt_form.addRow("服务器:", self.mqtt_host)
        mqtt_form.addRow("端口:", self.mqtt_port)
        mqtt_form.addRow("Client ID:", self.mqtt_client_id)
        mqtt_form.addRow("用户名:", self.mqtt_username)
        mqtt_form.addRow("密码:", self.mqtt_password)
        mqtt_form.addRow("QoS:", self.mqtt_qos)
        mqtt_form.addRow("", self.mqtt_use_tls)
        root.addWidget(self.mqtt_group)

        # TCP 配置区
        self.tcp_group = QGroupBox("TCP")
        tcp_form = QFormLayout(self.tcp_group)
        self.tcp_host = QLineEdit("localhost")
        self.tcp_port = QSpinBox()
        self.tcp_port.setRange(1, 65535)
        self.tcp_port.setValue(33410)
        tcp_form.addRow("服务器:", self.tcp_host)
        tcp_form.addRow("端口:", self.tcp_port)
        root.addWidget(self.tcp_group)
        self.tcp_group.setVisible(False)

        # 全局选项
        self.chk_change_only = QCheckBox("只在值变化时上报")
        self.chk_change_only.setChecked(True)
        self.chk_log_payload = QCheckBox("日志显示 payload")
        self.chk_log_payload.setChecked(True)
        opt_row = QHBoxLayout()
        opt_row.addWidget(self.chk_change_only)
        opt_row.addWidget(self.chk_log_payload)
        opt_row.addStretch(1)
        root.addLayout(opt_row)
        root.addStretch(1)

    def _wire(self) -> None:
        self.protocol_combo.currentIndexChanged.connect(self._on_protocol_changed)
        for w in [
            self.product_key, self.device_key, self.device_secret,
            self.mqtt_host, self.mqtt_port, self.mqtt_client_id,
            self.mqtt_username, self.mqtt_password, self.mqtt_qos,
            self.tcp_host, self.tcp_port,
        ]:
            if hasattr(w, "textChanged"):
                w.textChanged.connect(self._emit_config)
            elif hasattr(w, "valueChanged"):
                w.valueChanged.connect(self._emit_config)
        self.mqtt_use_tls.stateChanged.connect(self._emit_config)
        self.chk_change_only.stateChanged.connect(self._emit_config)
        self.chk_log_payload.stateChanged.connect(self._emit_config)

    def _on_protocol_changed(self, _idx: int) -> None:
        p = self.protocol_combo.currentData()
        is_mqtt = (p == PT.MQTT)
        self.mqtt_group.setVisible(is_mqtt)
        self.tcp_group.setVisible(not is_mqtt)
        self._emit_config()

    def _emit_config(self) -> None:
        self.config_changed.emit(self.get_config())

    # ----- 公共 API -----
    def get_config(self) -> DeviceConfig:
        return DeviceConfig(
            product_key=self.product_key.text().strip(),
            device_key=self.device_key.text().strip(),
            device_secret=self.device_secret.text().strip(),
            protocol=self.protocol_combo.currentData() or ProtocolType.MQTT,
            mqtt=MqttConfig(
                host=self.mqtt_host.text().strip() or "localhost",
                port=self.mqtt_port.value(),
                client_id=self.mqtt_client_id.text().strip(),
                username=self.mqtt_username.text().strip(),
                password=self.mqtt_password.text(),
                qos=self.mqtt_qos.value(),
                use_tls=self.mqtt_use_tls.isChecked(),
            ),
            tcp=TcpConfig(
                host=self.tcp_host.text().strip() or "localhost",
                port=self.tcp_port.value(),
            ),
            report_on_change_only=self.chk_change_only.isChecked(),
            enable_log_payload=self.chk_log_payload.isChecked(),
        )

    def set_config(self, cfg: DeviceConfig) -> None:
        self.product_key.setText(cfg.product_key)
        self.device_key.setText(cfg.device_key)
        self.device_secret.setText(cfg.device_secret)
        idx = self.protocol_combo.findData(cfg.protocol)
        if idx >= 0:
            self.protocol_combo.setCurrentIndex(idx)
        self.mqtt_host.setText(cfg.mqtt.host)
        self.mqtt_port.setValue(cfg.mqtt.port)
        self.mqtt_client_id.setText(cfg.mqtt.client_id)
        self.mqtt_username.setText(cfg.mqtt.username)
        self.mqtt_password.setText(cfg.mqtt.password)
        self.mqtt_qos.setValue(cfg.mqtt.qos)
        self.mqtt_use_tls.setChecked(cfg.mqtt.use_tls)
        self.tcp_host.setText(cfg.tcp.host)
        self.tcp_port.setValue(cfg.tcp.port)
        self.chk_change_only.setChecked(cfg.report_on_change_only)
        self.chk_log_payload.setChecked(cfg.enable_log_payload)
