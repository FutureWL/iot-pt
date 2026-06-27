"""服务面板 - 服务调用的自动/手动回复配置"""
from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QAbstractItemView,
    QCheckBox,
    QComboBox,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QPushButton,
    QSpinBox,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from ...core.config import ServiceConfig


class ServicePanel(QWidget):
    services_changed = Signal(list)

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._build_ui()
        self._wire()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        top = QHBoxLayout()
        top.addWidget(QLabel("<b>服务调用响应</b> <i>(下行 service/{id}/invoke → 自动/手动 reply)</i>"))
        top.addStretch(1)
        self.btn_add = QPushButton("+ 添加")
        self.btn_del = QPushButton("删除")
        top.addWidget(self.btn_add)
        top.addWidget(self.btn_del)
        root.addLayout(top)

        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels([
            "identifier", "自动回复", "延时(ms)", "code", "message", ""
        ])
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        hdr = self.table.horizontalHeader()
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        root.addWidget(self.table)

    def _wire(self) -> None:
        self.btn_add.clicked.connect(self._on_add)
        self.btn_del.clicked.connect(self._on_del)
        self.table.itemChanged.connect(lambda _: self._emit())

    def get_services(self) -> list[ServiceConfig]:
        out: list[ServiceConfig] = []
        for r in range(self.table.rowCount()):
            ident_item = self._cell(r, 0)
            if not ident_item:
                continue
            ident = ident_item.text().strip()
            if not ident:
                continue
            auto_item = self._cell(r, 1)
            auto = auto_item.checkState() == Qt.CheckState.Checked if auto_item else False
            try:
                delay_item = self._cell(r, 2)
                delay = int(delay_item.text() or "0") if delay_item else 0
            except ValueError:
                delay = 0
            try:
                code_item = self._cell(r, 3)
                code = int(code_item.text() or "0") if code_item else 0
            except ValueError:
                code = 0
            msg_item = self._cell(r, 4)
            msg = msg_item.text() if msg_item else "ok"
            out.append(ServiceConfig(
                identifier=ident,
                auto_reply=auto,
                reply_delay_ms=delay,
                reply_code=code,
                reply_message=msg,
            ))
        return out

    def set_services(self, cfgs: list[ServiceConfig]) -> None:
        self.table.blockSignals(True)
        self.table.setRowCount(0)
        for cfg in cfgs:
            self._append_row(cfg)
        self.table.blockSignals(False)
        self._emit()

    def _append_row(self, cfg: ServiceConfig) -> None:
        r = self.table.rowCount()
        self.table.insertRow(r)
        self.table.setItem(r, 0, QTableWidgetItem(cfg.identifier))
        auto_item = QTableWidgetItem()
        auto_item.setCheckState(Qt.CheckState.Checked if cfg.auto_reply else Qt.CheckState.Unchecked)
        self.table.setItem(r, 1, auto_item)
        self.table.setItem(r, 2, QTableWidgetItem(str(cfg.reply_delay_ms)))
        self.table.setItem(r, 3, QTableWidgetItem(str(cfg.reply_code)))
        self.table.setItem(r, 4, QTableWidgetItem(cfg.reply_message))
        del_btn = QPushButton("✕")
        del_btn.setFixedWidth(30)
        del_btn.clicked.connect(lambda _, rr=r: self._del_row(rr))
        self.table.setCellWidget(r, 5, del_btn)

    def _on_add(self) -> None:
        self._append_row(ServiceConfig(identifier=f"service_{self.table.rowCount() + 1}"))
        self._emit()

    def _on_del(self) -> None:
        rows = sorted({i.row() for i in self.table.selectedIndexes()}, reverse=True)
        for r in rows:
            self.table.removeRow(r)
        self._emit()

    def _del_row(self, row: int) -> None:
        self.table.removeRow(row)
        self._emit()

    def _cell(self, row: int, col: int) -> QTableWidgetItem:
        return self.table.item(row, col)

    def _emit(self) -> None:
        self.services_changed.emit(self.get_services())
