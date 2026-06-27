"""日志面板 - 实时显示 TX/RX/INFO"""
from __future__ import annotations

from datetime import datetime
from typing import Optional

from PySide6.QtCore import Qt
from PySide6.QtGui import QColor, QFont, QTextCharFormat, QTextCursor
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QFileDialog,
    QHBoxLayout,
    QLabel,
    QPlainTextEdit,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from ...protocol.messages import LogEntry


_LEVEL_COLORS = {
    "INFO":  QColor("#909399"),
    "WARN":  QColor("#E6A23C"),
    "ERROR": QColor("#F56C6C"),
    "DEBUG": QColor("#67C23A"),
}

_DIR_COLORS = {
    "TX":   QColor("#409EFF"),
    "RX":   QColor("#67C23A"),
}


class LogPanel(QWidget):
    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._max_lines = 2000
        self._build_ui()
        self._wire()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)

        top = QHBoxLayout()
        self.chk_autoscroll = QCheckBox("自动滚动")
        self.chk_autoscroll.setChecked(True)
        self.level_filter = QComboBox()
        self.level_filter.addItems(["ALL", "INFO", "WARN", "ERROR", "DEBUG"])
        self.btn_clear = QPushButton("清空")
        self.btn_export = QPushButton("导出...")
        top.addWidget(QLabel("日志:"))
        top.addWidget(self.chk_autoscroll)
        top.addWidget(QLabel("级别:"))
        top.addWidget(self.level_filter)
        top.addStretch(1)
        top.addWidget(self.btn_clear)
        top.addWidget(self.btn_export)
        root.addLayout(top)

        self.text = QPlainTextEdit()
        self.text.setReadOnly(True)
        self.text.setMaximumBlockCount(self._max_lines)
        font = QFont("Monospace", 9)
        font.setStyleHint(QFont.StyleHint.TypeWriter)
        self.text.setFont(font)
        root.addWidget(self.text)

    def _wire(self) -> None:
        self.btn_clear.clicked.connect(self.text.clear)
        self.btn_export.clicked.connect(self._on_export)

    def append(self, entry: LogEntry) -> None:
        # 过滤
        cur_level = self.level_filter.currentText()
        if cur_level != "ALL" and entry.level != cur_level:
            return

        ts = datetime.fromtimestamp(entry.ts).strftime("%H:%M:%S.%f")[:-3]
        body = entry.payload if isinstance(entry.payload, str) else str(entry.payload)
        if len(body) > 500:
            body = body[:500] + "..."

        if entry.direction in ("TX", "RX"):
            line = f"{ts}  {entry.direction}  {entry.topic}  {body}"
            color = _DIR_COLORS.get(entry.direction, QColor("#909399"))
        else:
            line = f"{ts}  {entry.level:<5}  {body}"
            color = _LEVEL_COLORS.get(entry.level, QColor("#909399"))

        # 用 QTextCharFormat 给整行上色
        cursor = self.text.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        fmt = QTextCharFormat()
        fmt.setForeground(color)
        cursor.insertText(line + "\n", fmt)

        # 自动滚动
        if self.chk_autoscroll.isChecked():
            sb = self.text.verticalScrollBar()
            sb.setValue(sb.maximum())

    def _on_export(self) -> None:
        path, _ = QFileDialog.getSaveFileName(
            self, "导出日志", "simulator.log", "Log files (*.log);;All files (*)"
        )
        if not path:
            return
        with open(path, "w", encoding="utf-8") as f:
            f.write(self.text.toPlainText())
