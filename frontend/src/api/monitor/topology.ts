import request from '@/api/request'

/** 节点类型(电力设备分类) */
export type TopologyNodeType =
  | 'substation'    // 变电站
  | 'transformer'   // 主变
  | 'busbar'        // 母线
  | 'switch'        // 开关/断路器
  | 'ring_main'     // 环网柜
  | 'junction'      // 电缆分接箱
  | 'meter'         // 用户/电表

/** 电压等级 */
export type VoltageLevel = '110kV' | '35kV' | '10kV' | '0.4kV'

/** 节点状态 */
export type NodeStatus = 'normal' | 'warning' | 'fault' | 'offline'

/** 边类型 */
export type EdgeType = 'bus' | 'feeder' | 'cable' | 'tie'

export interface TopologyNode {
  id: string
  name: string
  type: TopologyNodeType
  voltageLevel: VoltageLevel
  status: NodeStatus
  /** 关联真实设备 id(便于跳转到设备详情) */
  deviceId?: number
  /** 区域内分组标签 */
  region?: string
  /** 子站编号 */
  substationCode?: string
  /** 额外属性(自由扩展) */
  properties?: Record<string, any>
}

export interface TopologyEdge {
  id: string
  source: string
  target: string
  type: EdgeType
  status: NodeStatus
  /** 边标签(线缆型号 / 长度 / 编号) */
  label?: string
}

export interface TopologyGraph {
  region: string
  rootNodeId: string
  nodes: TopologyNode[]
  edges: TopologyEdge[]
  stats: {
    nodeCount: number
    edgeCount: number
    faultCount: number
    warningCount: number
  }
}

/** 区域列表(树状) */
export interface TopologyRegionVO {
  id: string
  name: string
  parentId?: string
  nodeCount: number
  faultCount: number
}

/**
 * 拉取某区域的全网拓扑
 * @param regionId 区域 id(可选,缺省返回全部聚合)
 * @param depth 展开层级(1~3,默认 2)
 */
export function getTopologyGraph(regionId?: string, depth = 2) {
  return request<TopologyGraph>({
    url: '/monitor/topology/graph',
    method: 'get',
    params: { regionId, depth }
  })
}

export function listTopologyRegions() {
  return request<TopologyRegionVO[]>({
    url: '/monitor/topology/regions',
    method: 'get'
  })
}

export function getTopologyNodeDetail(nodeId: string) {
  return request<TopologyNode & { connectedDevices: TopologyNode[]; recentAlerts: any[] }>({
    url: `/monitor/topology/node/${nodeId}`,
    method: 'get'
  })
}