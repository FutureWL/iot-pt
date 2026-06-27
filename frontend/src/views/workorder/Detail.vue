<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh, Check, User, Clock, Bell } from '@element-plus/icons-vue'
import {
  getWorkOrder,
  getWorkOrderLogs,
  assignWorkOrder,
  completeWorkOrder,
  type WorkOrderVO,
  type WorkOrderLogVO
} from '@/api/workorder'

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))

const loading = ref(false)
const submitting = ref(false)
const detail = ref<WorkOrderVO | null>(null)
const logs = ref<WorkOrderLogVO[]>([])

async function load() {
  loading.value = true
  try {
    const [dRes, lRes]: any[] = await Promise.all([
      getWorkOrder(id.value),
      getWorkOrderLogs(id.value)
    ])
    detail.value = dRes.data ?? null
    logs.value = lRes.data ?? []
  } finally {
    loading.value = false
  }
}

async function onAssign() {
  if (!detail.value) return
  const { value: assignee } = await ElMessageBox.prompt('输入处理人用户名', '派单', {
    inputPlaceholder: '用户名', confirmButtonText: '派单', cancelButtonText: '取消'
  }).catch(() => ({ value: '' }))
  if (!assignee) return
  submitting.value = true
  try {
    await assignWorkOrder(detail.value.id, assignee)
    ElMessage.success('派单成功')
    load()
  } finally { submitting.value = false }
}

async function onComplete() {
  if (!detail.value) return
  const { value: remark } = await ElMessageBox.prompt('处理说明(可空)', '完成工单', {
    inputPlaceholder: '说明', confirmButtonText: '完成', cancelButtonText: '取消'
  }).catch(() => ({ value: '' }))
  submitting.value = true
  try {
    await completeWorkOrder(detail.value.id, remark)
    ElMessage.success('已完成')
    load()
  } finally { submitting.value = false }
}

function onRemind() {
  ElMessage.info('催办提醒:待集成通知渠道后启用')
}

function goBack() {
  router.push('/workorder/list')
}

const statusTagMap: Record<string, { label: string; type: string }> = {
  PENDING: { label: '待派单', type: 'info' },
  PROCESSING: { label: '处理中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  OVERDUE: { label: '已超时', type: 'danger' },
  CLOSED: { label: '已关闭', type: 'info' }
}

onMounted(load)
</script>

<template>
  <div class="page-container detail-page" v-loading="loading">
    <div class="page-header">
      <el-button :icon="ArrowLeft" link @click="goBack">返回列表</el-button>
      <span class="page-title-spacer"></span>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <template v-if="detail">
      <!-- 概览卡 -->
      <div class="page-card mb-16">
        <div class="header-row">
          <div>
            <h2 class="detail-title">{{ detail.title }}</h2>
            <div class="detail-meta">
              <el-tag size="small" type="info">{{ detail.workOrderNo }}</el-tag>
              <el-tag :type="statusTagMap[detail.status]?.type as any" size="small" class="ml-8">
                {{ statusTagMap[detail.status]?.label }}
              </el-tag>
              <span class="text-secondary ml-8">优先级 {{ detail.priority }}</span>
            </div>
          </div>
          <div class="header-actions">
            <el-button v-if="detail.status === 'PENDING'" type="primary" :icon="User" :loading="submitting" @click="onAssign">派单</el-button>
            <el-button v-if="detail.status === 'PROCESSING'" type="success" :icon="Check" :loading="submitting" @click="onComplete">完成</el-button>
            <el-button v-if="detail.status === 'PROCESSING'" :icon="Bell" @click="onRemind">催办</el-button>
          </div>
        </div>
      </div>

      <el-row :gutter="16">
        <!-- 左:工单信息 + SOP 关联 -->
        <el-col :xs="24" :md="16">
          <div class="page-card mb-16">
            <h3 class="card-title">基本信息</h3>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="工单号">{{ detail.workOrderNo }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="statusTagMap[detail.status]?.type as any">{{ statusTagMap[detail.status]?.label }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="创建人">{{ detail.creator }}</el-descriptions-item>
              <el-descriptions-item label="处理人">{{ detail.assignee || '未派单' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ detail.completedAt || '—' }}</el-descriptions-item>
              <el-descriptions-item label="SLA 截止">
                <span :class="{ 'text-danger': detail.slaDeadline && new Date(detail.slaDeadline).getTime() < Date.now() }">
                  {{ detail.slaDeadline || '—' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="关联告警">
                <el-link v-if="detail.alertId" type="primary" @click="router.push('/alert/center')">#{{ detail.alertId }}</el-link>
                <span v-else>—</span>
              </el-descriptions-item>
              <el-descriptions-item label="设备 Key" :span="2">
                <el-tag size="small" type="info">{{ detail.deviceKey }}</el-tag>
                <span class="text-secondary ml-8">{{ detail.deviceName }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="问题描述" :span="2">{{ detail.description || '—' }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <div class="page-card">
            <h3 class="card-title">关联 SOP / 知识库</h3>
            <el-empty description="暂未关联标准作业指导书,请到知识库选择" />
            <div class="text-secondary text-xs">知识库 API 已就绪,后端补全关联接口后此处将显示推荐 SOP。</div>
          </div>
        </el-col>

        <!-- 右:处理时间轴 -->
        <el-col :xs="24" :md="8">
          <div class="page-card">
            <h3 class="card-title"><el-icon><Clock /></el-icon> 处理时间轴</h3>
            <el-timeline v-if="logs.length > 0">
              <el-timeline-item
                v-for="log in logs" :key="log.id"
                :timestamp="log.ts" placement="top">
                <div class="log-action">{{ log.action }}</div>
                <div class="log-operator text-secondary text-xs">操作人: {{ log.operator }}</div>
                <div v-if="log.remark" class="log-remark">{{ log.remark }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无处理记录" :image-size="80" />
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.detail-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; }
.page-title-spacer { flex: 1; }
.mb-16 { margin-bottom: $spacing-16; }
.ml-8 { margin-left: $spacing-8; }

.header-row { display: flex; justify-content: space-between; align-items: flex-start; gap: $spacing-16; flex-wrap: wrap; }
.detail-title { margin: 0 0 $spacing-8; font-size: $font-size-large; color: var(--iot-text-primary); }
.detail-meta { display: flex; align-items: center; }
.header-actions { display: flex; gap: $spacing-8; flex-shrink: 0; }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}

.log-action { font-weight: $font-weight-medium; color: var(--iot-text-primary); margin-bottom: $spacing-4; }
.log-operator { margin-bottom: $spacing-4; }
.log-remark { font-size: $font-size-small; color: var(--iot-text-regular); background: var(--iot-bg-page); padding: $spacing-8; border-radius: $radius-base; margin-top: $spacing-4; }
</style>