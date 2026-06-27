#!/bin/bash
# 启动 IoT 设备模拟器(4K HiDPI 缩放 1.5x)
# 调整 QT_SCALE_FACTOR:1.0/1.25/1.5/1.75/2.0 对应 100/125/150/175/200%
export QT_SCALE_FACTOR=${QT_SCALE_FACTOR:-1.5}
export QT_ENABLE_HIGHDPI_SCALING=1
exec iot-simulator "$@"
