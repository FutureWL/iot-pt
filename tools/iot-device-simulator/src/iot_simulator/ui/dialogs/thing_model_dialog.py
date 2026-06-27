"""物模型导入对话框 - 手填 JSON 文本"""
from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import (
    QDialog,
    QDialogButtonBox,
    QHBoxLayout,
    QLabel,
    QPlainTextEdit,
    QPushButton,
    QVBoxLayout,
    QFileDialog,
    QMessageBox,
)


class ThingModelDialog(QDialog):
    """粘贴/输入物模型 JSON,返回解析后的对象"""
    thing_model_loaded = Signal(str)   # JSON 字符串

    def __init__(self, current: str = "", parent=None) -> None:
        super().__init__(parent)
        self.setWindowTitle("物模型导入")
        self.resize(640, 480)
        self._build_ui()
        self.text.setPlainText(current)

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)
        root.addWidget(QLabel(
            "<b>粘贴/输入物模型 JSON:</b><br>"
            "<i>格式参考阿里云物模型: {properties:[], events:[], services:[]}</i>"
        ))
        self.text = QPlainTextEdit()
        self.text.setPlaceholderText('{"properties": [{"identifier": "temperature", ...}], ...}')
        root.addWidget(self.text)

        btn_row = QHBoxLayout()
        self.btn_load_file = QPushButton("从文件加载...")
        self.btn_template = QPushButton("插入示例模板")
        btn_row.addWidget(self.btn_load_file)
        btn_row.addWidget(self.btn_template)
        btn_row.addStretch(1)
        root.addLayout(btn_row)

        bb = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        bb.accepted.connect(self._on_ok)
        bb.rejected.connect(self.reject)
        root.addWidget(bb)

        self.btn_load_file.clicked.connect(self._on_load_file)
        self.btn_template.clicked.connect(self._on_template)

    def _on_ok(self) -> None:
        raw = self.text.toPlainText().strip()
        if not raw:
            self.thing_model_loaded.emit("")
            self.accept()
            return
        # 验证
        try:
            from ...model.thing_model import ThingModel
            ThingModel.from_json(raw)
        except Exception as e:
            QMessageBox.warning(self, "JSON 解析失败", str(e))
            return
        self.thing_model_loaded.emit(raw)
        self.accept()

    def _on_load_file(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self, "选择物模型 JSON", "", "JSON files (*.json);;All files (*)"
        )
        if not path:
            return
        try:
            with open(path, "r", encoding="utf-8") as f:
                self.text.setPlainText(f.read())
        except OSError as e:
            QMessageBox.warning(self, "读取失败", str(e))

    def _on_template(self) -> None:
        template = """{
  "version": "1.0",
  "properties": [
    {
      "identifier": "temperature",
      "name": "温度",
      "accessMode": "RO",
      "required": true,
      "dataType": { "type": "double", "specs": { "min": -40, "max": 125, "unit": "℃", "precision": 0.1 } }
    },
    {
      "identifier": "humidity",
      "name": "湿度",
      "accessMode": "RO",
      "dataType": { "type": "double", "specs": { "min": 0, "max": 100, "unit": "%", "precision": 0.1 } }
    },
    {
      "identifier": "switch",
      "name": "开关",
      "accessMode": "RW",
      "dataType": { "type": "boolean" }
    }
  ],
  "events": [
    {
      "identifier": "high_temp_alarm",
      "name": "高温告警",
      "type": "ERROR",
      "output": [
        { "identifier": "temperature", "dataType": { "type": "double" } }
      ]
    }
  ],
  "services": [
    {
      "identifier": "reboot",
      "name": "重启",
      "callType": "ASYNC",
      "input": [],
      "output": []
    }
  ]
}"""
        self.text.setPlainText(template)
