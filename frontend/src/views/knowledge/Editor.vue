<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Reading } from '@element-plus/icons-vue'
import {
  getKnowledgeDetail,
  createKnowledge,
  updateKnowledge,
  type KnowledgeDetailVO
} from '@/api/knowledge'

const route = useRoute()
const router = useRouter()
const id = computed(() => route.params.id ? Number(route.params.id) : null)

const submitting = ref(false)
const form = reactive<Partial<KnowledgeDetailVO>>({
  id: undefined,
  category: '',
  title: '',
  summary: '',
  content: '',
  tags: '',
  status: 'DRAFT'
})

const categories = ['故障处理', '巡检作业', '应急处置', '设备维护', '基础知识']
const statuses: Array<{ value: KnowledgeDetailVO['status']; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'ARCHIVED', label: '已归档' }
]

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const formRef = ref()

async function loadIfEdit() {
  if (id.value == null) return
  const res: any = await getKnowledgeDetail(id.value)
  const d = res.data
  if (!d) return
  Object.assign(form, {
    id: d.id, category: d.category, title: d.title, summary: d.summary ?? '',
    content: d.content, tags: d.tags ?? '', status: d.status
  })
}

async function onSubmit() {
  if (!formRef.value) return
  // eslint-disable-next-line no-useless-assignment
  let valid = false
  try { valid = await formRef.value.validate() } catch { valid = false }
  if (!valid) return
  submitting.value = true
  try {
    if (form.id) {
      await updateKnowledge(form)
      ElMessage.success('已保存')
    } else {
      const res: any = await createKnowledge(form)
      ElMessage.success('已创建')
      if (res.data?.id) router.replace(`/knowledge/editor/${res.data.id}`)
    }
  } finally { submitting.value = false }
}

function goBack() { router.push('/knowledge/list') }

onMounted(loadIfEdit)
</script>

<template>
  <div class="page-container editor-page">
    <div class="page-header">
      <el-button
        :icon="ArrowLeft"
        link
        @click="goBack"
      >
        返回列表
      </el-button>
      <span class="page-title-spacer" />
      <el-button
        type="primary"
        :icon="Check"
        :loading="submitting"
        @click="onSubmit"
      >
        {{ form.id ? '保存' : '创建' }}
      </el-button>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      class="editor-form"
    >
      <div class="page-card mb-16">
        <h3 class="card-title">
          <el-icon><Reading /></el-icon> 文档信息
        </h3>
        <el-row :gutter="16">
          <el-col
            :xs="24"
            :sm="12"
          >
            <el-form-item
              label="标题"
              prop="title"
            >
              <el-input
                v-model="form.title"
                placeholder="文档标题"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col
            :xs="24"
            :sm="6"
          >
            <el-form-item
              label="分类"
              prop="category"
            >
              <el-select
                v-model="form.category"
                placeholder="选择分类"
                style="width: 100%"
              >
                <el-option
                  v-for="c in categories"
                  :key="c"
                  :label="c"
                  :value="c"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col
            :xs="24"
            :sm="6"
          >
            <el-form-item label="状态">
              <el-select
                v-model="form.status"
                style="width: 100%"
              >
                <el-option
                  v-for="s in statuses"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="摘要">
              <el-input
                v-model="form.summary"
                placeholder="一句话描述(可选)"
                maxlength="200"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标签">
              <el-input
                v-model="form.tags"
                placeholder="多个标签用英文逗号分隔,如 局放,母排,10kV"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="page-card">
        <h3 class="card-title">
          正文内容
        </h3>
        <el-form-item prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="18"
            resize="vertical"
            placeholder="支持 Markdown / 富文本格式,可用作标准作业指导书(SOP)"
          />
        </el-form-item>
        <div class="text-secondary text-xs">
          提示:后续可接入富文本编辑器(目前为纯文本)。
          关键操作步骤建议使用编号列表;应急处置 SOP 请明确"判断 → 操作 → 验证"三段式。
        </div>
      </div>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.editor-page { background: var(--iot-bg-page); }
.page-header { display: flex; align-items: center; gap: $spacing-12; margin-bottom: $spacing-16; }
.page-title-spacer { flex: 1; }
.mb-16 { margin-bottom: $spacing-16; }

.card-title {
  font-size: $font-size-medium; margin: 0 0 $spacing-16;
  color: var(--iot-text-primary); display: flex; align-items: center; gap: $spacing-8;
  &::before { content: ''; display: block; width: 3px; height: 14px; background: var(--iot-color-primary); }
}

.editor-form { max-width: 1200px; }
</style>