"""属性面板 - 物模型驱动的属性生成器表格"""
from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QAbstractItemView,
    QComboBox,
    QDoubleSpinBox,
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

from ...core.config import PropertyGenConfig
from ...model.enums import GenStrategy


_STRATEGY_LABELS = {
    GenStrategy.RANDOM: "随机",
    GenStrategy.INCREMENT: "递增",
    GenStrategy.CONST: "固定",
    GenStrategy.SINE: "正弦波",
}

_TYPE_OPTIONS = ["int", "double", "boolean", "text"]


class PropertyPanel(QWidget):
    """属性生成器表格"""
    properties_changed = Signal(list)   # list[PropertyGenConfig]

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._build_ui()
        self._wire()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)

        # 标题栏
        top = QHBoxLayout()
        top.addWidget(QLabel("<b>属性</b> <i>(从物模型自动生成,也可手动添加)</i>"))
        top.addStretch(1)
        self.btn_add = QPushButton("+ 添加")
        self.btn_del = QPushButton("删除")
        top.addWidget(self.btn_add)
        top.addWidget(self.btn_del)
        root.addLayout(top)

        # 表格
        self.table = QTableWidget(0, 7)
        self.table.setHorizontalHeaderLabels([
            "identifier", "类型", "策略", "参数", "周期(ms)", "当前值", ""
        ])
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.DoubleClicked)
        hdr = self.table.horizontalHeader()
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        root.addWidget(self.table)

    def _wire(self) -> None:
        self.btn_add.clicked.connect(self._on_add)
        self.btn_del.clicked.connect(self._on_del)
        self.table.itemChanged.connect(lambda _: self._emit())

    # ----- 公共 API -----
    def get_properties(self) -> list[PropertyGenConfig]:
        out: list[PropertyGenConfig] = []
        for r in range(self.table.rowCount()):
            ident_item = self._cell(r, 0)
            if not ident_item:
                continue
            ident = ident_item.text().strip()
            if not ident:
                continue
            # 类型列:可能是 QTableWidgetItem
            type_item = self._cell(r, 1)
            type_ = type_item.text() if type_item else "double"
            # 策略列:QComboBox widget
            strategy_str = self._combo_data(r, 2) or GenStrategy.RANDOM.value
            # 参数列
            params_item = self._cell(r, 3)
            params_str = params_item.text() if params_item else ""
            # 周期列
            try:
                interval_item = self._cell(r, 4)
                interval = int(interval_item.text() or "1000") if interval_item else 1000
            except ValueError:
                interval = 1000
            cfg = self._parse_params(strategy_str, params_str)
            cfg["identifier"] = ident
            cfg["type"] = type_
            cfg["strategy"] = strategy_str
            cfg["report_interval_ms"] = interval
            out.append(PropertyGenConfig(**cfg))
        return out

    def set_properties(self, cfgs: list[PropertyGenConfig]) -> None:
        self.table.blockSignals(True)
        self.table.setRowCount(0)
        for cfg in cfgs:
            self._append_row(cfg)
        self.table.blockSignals(False)
        self._emit()

    def populate_from_thing_model(
        self, thing_model_json: str, existing: list[PropertyGenConfig] | None = None
    ) -> None:
        """从物模型自动生成属性列表(保留已有配置)"""
        from ...model.thing_model import ThingModel

        try:
            tm = ThingModel.from_json(thing_model_json)
        except ValueError:
            return
        existing_map = {c.identifier: c for c in (existing or [])}
        cfgs: list[PropertyGenConfig] = []
        for p in tm.properties:
            t = (p.data_type.get("type") or "text").lower()
            if t in ("int", "long"):
                cfg_type = "int"
            elif t in ("double", "float"):
                cfg_type = "double"
            elif t in ("boolean",):
                cfg_type = "boolean"
            else:
                cfg_type = "text"

            # 保留已有配置
            old = existing_map.get(p.identifier)
            if old:
                cfgs.append(old)
                continue

            # 从物模型 spec 推断参数
            specs = p.data_type.get("specs") or {}
            mn = specs.get("min", 0)
            mx = specs.get("max", 100 if cfg_type != "boolean" else 1)
            precision = specs.get("precision", 2)
            unit = specs.get("unit", "")
            base = (mn + mx) / 2 if isinstance(mn, (int, float)) and isinstance(mx, (int, float)) else 25
            amplitude = (mx - mn) / 2 if isinstance(mn, (int, float)) and isinstance(mx, (int, float)) else 5

            cfgs.append(PropertyGenConfig(
                identifier=p.identifier,
                type=cfg_type,
                strategy=GenStrategy.SINE if cfg_type in ("int", "double") else GenStrategy.RANDOM,
                min=float(mn),
                max=float(mx),
                precision=int(precision) if precision else 2,
                initial=base,
                base=base,
                amplitude=amplitude,
                period_sec=60.0,
                step=1.0,
                report_interval_ms=1000,
            ))
        self.set_properties(cfgs)

    # ----- 内部 -----
    def _cell(self, row: int, col: int) -> QTableWidgetItem:
        return self.table.item(row, col)

    def _on_add(self) -> None:
        cfg = PropertyGenConfig(
            identifier=f"new_prop_{self.table.rowCount() + 1}",
            type="double",
            strategy=GenStrategy.RANDOM,
            min=0,
            max=100,
            precision=2,
        )
        self._append_row(cfg)
        self._emit()

    def _on_del(self) -> None:
        rows = sorted({i.row() for i in self.table.selectedIndexes()}, reverse=True)
        for r in rows:
            self.table.removeRow(r)
        self._emit()

    def _append_row(self, cfg: PropertyGenConfig) -> None:
        r = self.table.rowCount()
        self.table.insertRow(r)
        # identifier
        self.table.setItem(r, 0, QTableWidgetItem(cfg.identifier))
        # 类型
        type_item = QTableWidgetItem(cfg.type)
        type_item.setData(Qt.ItemDataRole.UserRole, _TYPE_OPTIONS)
        self.table.setItem(r, 1, type_item)
        # 策略
        strat_combo = QComboBox()
        for s in GenStrategy:
            strat_combo.addItem(_STRATEGY_LABELS.get(s, s.value), s.value)
        if cfg.strategy.value in [self._strategy_data(i) for i in range(strat_combo.count())]:
            strat_combo.setCurrentIndex(
                [self._strategy_data(i) for i in range(strat_combo.count())].index(cfg.strategy.value)
            )
        self.table.setCellWidget(r, 2, strat_combo)
        # 参数
        params_str = self._format_params(cfg)
        self.table.setItem(r, 3, QTableWidgetItem(params_str))
        # 周期
        self.table.setItem(r, 4, QTableWidgetItem(str(cfg.report_interval_ms)))
        # 当前值
        v = cfg.initial if cfg.initial is not None else cfg.base
        self.table.setItem(r, 5, QTableWidgetItem(str(v) if v is not None else "—"))
        # 删除按钮
        del_btn = QPushButton("✕")
        del_btn.setFixedWidth(30)
        del_btn.clicked.connect(lambda _, rr=r: self._del_row(rr))
        self.table.setCellWidget(r, 6, del_btn)

    def _strategy_data(self, idx: int) -> str:
        from PySide6.QtWidgets import QComboBox
        # 内部方法: 通过 idx 拿 data
        combo: QComboBox = self.table.cellWidget(0, 2) if self.table.rowCount() > 0 else None
        if combo is None:
            return ""
        return combo.itemData(idx)

    def _combo_data(self, row: int, col: int) -> str | None:
        """从指定行列的 QComboBox widget 拿 currentData"""
        from PySide6.QtWidgets import QComboBox
        w = self.table.cellWidget(row, col)
        if isinstance(w, QComboBox):
            return w.currentData()
        # 如果不是 widget 可能是 item (枚举 存储在 UserRole)
        item = self._cell(row, col)
        if item:
            data = item.data(Qt.ItemDataRole.UserRole)
            return data
        return None

    def _del_row(self, row: int) -> None:
        self.table.removeRow(row)
        self._emit()

    def _format_params(self, cfg: PropertyGenConfig) -> str:
        s = cfg.strategy
        if s == GenStrategy.RANDOM:
            return f"min={cfg.min}, max={cfg.max}, precision={cfg.precision}"
        if s == GenStrategy.INCREMENT:
            return f"start={cfg.initial}, step={cfg.step}, max={cfg.max}, wrap=auto"
        if s == GenStrategy.CONST:
            return f"value={cfg.initial}"
        if s == GenStrategy.SINE:
            return f"base={cfg.base}, amplitude={cfg.amplitude}, period={cfg.period_sec}s"
        return ""

    def _parse_params(self, strategy: str, params_str: str) -> dict:
        """极简参数解析(键=值,逗号分隔)"""
        out: dict = {}
        for piece in params_str.split(","):
            piece = piece.strip()
            if "=" in piece:
                k, v = piece.split("=", 1)
                k = k.strip()
                v = v.strip()
                if k in ("min", "max", "step", "base", "amplitude", "initial", "value", "start"):
                    try:
                        out[k] = float(v) if "." in v else int(v)
                    except ValueError:
                        out[k] = v
                elif k == "precision":
                    try:
                        out[k] = int(v)
                    except ValueError:
                        out[k] = 2
                elif k == "period":
                    try:
                        out["period_sec"] = float(v.replace("s", ""))
                    except ValueError:
                        out["period_sec"] = 60.0
        return out

    def _emit(self) -> None:
        self.properties_changed.emit(self.get_properties())
