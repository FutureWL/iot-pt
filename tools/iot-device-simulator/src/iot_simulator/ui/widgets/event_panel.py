"""事件面板 - 手动/定时/阈值触发"""
from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QAbstractItemView,
    QComboBox,
    QDoubleSpinBox,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QPushButton,
    QSpinBox,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from ...core.config import EventTriggerConfig
from ...model.enums import TriggerType


_TRIGGER_LABELS = {
    TriggerType.MANUAL: "手动",
    TriggerType.TIMER: "定时",
    TriggerType.THRESHOLD: "阈值",
}

_LEVEL_OPTIONS = ["INFO", "WARN", "ERROR", "CRITICAL"]


class EventPanel(QWidget):
    """事件配置表格"""
    events_changed = Signal(list)   # list[EventTriggerConfig]
    fire_manual = Signal(str)         # 手动触发

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._build_ui()
        self._wire()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)

        top = QHBoxLayout()
        top.addWidget(QLabel("<b>事件</b>"))
        top.addStretch(1)
        self.btn_add = QPushButton("+ 添加")
        self.btn_del = QPushButton("删除")
        self.btn_fire = QPushButton("🔥 触发选中")
        top.addWidget(self.btn_fire)
        top.addWidget(self.btn_add)
        top.addWidget(self.btn_del)
        root.addLayout(top)

        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels([
            "identifier", "级别", "触发方式", "参数", "输出 (JSON)", ""
        ])
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        hdr = self.table.horizontalHeader()
        hdr.setSectionResizeMode(4, QHeaderView.ResizeMode.Stretch)
        root.addWidget(self.table)

    def _wire(self) -> None:
        self.btn_add.clicked.connect(self._on_add)
        self.btn_del.clicked.connect(self._on_del)
        self.btn_fire.clicked.connect(self._on_fire)
        self.table.itemChanged.connect(lambda _: self._emit())

    def get_events(self) -> list[EventTriggerConfig]:
        out: list[EventTriggerConfig] = []
        for r in range(self.table.rowCount()):
            ident_item = self._cell(r, 0)
            if not ident_item:
                continue
            ident = ident_item.text().strip()
            if not ident:
                continue
            # 级别列:QComboBox widget
            level = self._combo_text(r, 1) or "INFO"
            # 触发方式列:QComboBox widget
            trigger_str = self._combo_data(r, 2) or TriggerType.MANUAL.value
            # 参数列
            params_item = self._cell(r, 3)
            params_str = params_item.text() if params_item else ""
            # 输出列
            output_item = self._cell(r, 4)
            output_str = output_item.text() if output_item else ""
            cfg = EventTriggerConfig(
                identifier=ident,
                type=level,
                trigger_type=TriggerType(trigger_str),
            )
            # 解析 params
            for piece in params_str.split(","):
                piece = piece.strip()
                if "=" in piece:
                    k, v = piece.split("=", 1)
                    k = k.strip()
                    v = v.strip()
                    if k == "interval" or k == "interval_sec":
                        try: cfg.interval_sec = float(v.replace("s", ""))
                        except ValueError: pass
                    elif k == "watch":
                        cfg.watch_identifier = v
                    elif k == "op":
                        cfg.op = v
                    elif k == "threshold":
                        try: cfg.threshold = float(v)
                        except ValueError: pass
            # 解析 output (JSON 字典字符串)
            import json
            try:
                cfg.output = json.loads(output_str) if output_str.strip() else {}
            except json.JSONDecodeError:
                cfg.output = {}
            out.append(cfg)
        return out

    def set_events(self, cfgs: list[EventTriggerConfig]) -> None:
        self.table.blockSignals(True)
        self.table.setRowCount(0)
        for cfg in cfgs:
            self._append_row(cfg)
        self.table.blockSignals(False)
        self._emit()

    def _append_row(self, cfg: EventTriggerConfig) -> None:
        r = self.table.rowCount()
        self.table.insertRow(r)
        self.table.setItem(r, 0, QTableWidgetItem(cfg.identifier))
        # 级别
        level_combo = QComboBox()
        for lv in _LEVEL_OPTIONS:
            level_combo.addItem(lv)
        if cfg.type in _LEVEL_OPTIONS:
            level_combo.setCurrentText(cfg.type)
        self.table.setCellWidget(r, 1, level_combo)
        # 触发方式
        trig_combo = QComboBox()
        for t in TriggerType:
            trig_combo.addItem(_TRIGGER_LABELS.get(t, t.value), t.value)
        if cfg.trigger_type in TriggerType:
            idx = trig_combo.findData(cfg.trigger_type.value)
            if idx >= 0:
                trig_combo.setCurrentIndex(idx)
        self.table.setCellWidget(r, 2, trig_combo)
        # 参数
        params = self._format_params(cfg)
        self.table.setItem(r, 3, QTableWidgetItem(params))
        # 输出
        import json
        try:
            output_str = json.dumps(cfg.output, ensure_ascii=False)
        except (TypeError, ValueError):
            output_str = "{}"
        self.table.setItem(r, 4, QTableWidgetItem(output_str))
        # 删除
        del_btn = QPushButton("✕")
        del_btn.setFixedWidth(30)
        del_btn.clicked.connect(lambda _, rr=r: self._del_row(rr))
        self.table.setCellWidget(r, 5, del_btn)

    def _format_params(self, cfg: EventTriggerConfig) -> str:
        if cfg.trigger_type == TriggerType.TIMER:
            return f"interval={cfg.interval_sec}s"
        if cfg.trigger_type == TriggerType.THRESHOLD:
            return f"watch={cfg.watch_identifier}, op={cfg.op}, threshold={cfg.threshold}"
        return ""

    def _on_add(self) -> None:
        cfg = EventTriggerConfig(
            identifier=f"event_{self.table.rowCount() + 1}",
            type="INFO",
            trigger_type=TriggerType.MANUAL,
            output={},
        )
        self._append_row(cfg)
        self._emit()

    def _on_del(self) -> None:
        rows = sorted({i.row() for i in self.table.selectedIndexes()}, reverse=True)
        for r in rows:
            self.table.removeRow(r)
        self._emit()

    def _on_fire(self) -> None:
        rows = sorted({i.row() for i in self.table.selectedIndexes()})
        for r in rows:
            ident = self._cell(r, 0).text().strip()
            if ident:
                self.fire_manual.emit(ident)

    def _del_row(self, row: int) -> None:
        self.table.removeRow(row)
        self._emit()

    def _cell(self, row: int, col: int) -> QTableWidgetItem:
        return self.table.item(row, col)

    def _combo_data(self, row: int, col: int) -> str | None:
        from PySide6.QtWidgets import QComboBox
        w = self.table.cellWidget(row, col)
        if isinstance(w, QComboBox):
            return w.currentData()
        return None

    def _combo_text(self, row: int, col: int) -> str | None:
        from PySide6.QtWidgets import QComboBox
        w = self.table.cellWidget(row, col)
        if isinstance(w, QComboBox):
            return w.currentText()
        return None

    def _emit(self) -> None:
        self.events_changed.emit(self.get_events())
