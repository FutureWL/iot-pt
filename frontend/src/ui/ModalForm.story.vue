<script setup lang="ts">
/**
 * ModalForm 故事 — 表单对话框
 *
 * 展示 ModalForm 的多种典型场景:基础表单 / 窄表单 / 加载中。
 */
import { ref } from 'vue'
import ModalForm from './ModalForm.vue'
</script>

<template>
  <Story title="ModalForm" group="basic" :layout="{ type: 'grid', width: 600 }">
    <!-- 用户表单 -->
    <Variant title="用户表单">
      <template #setup>
        { () => {
          const visible = ref(true)
          const form = ref({ username: '', email: '', role: '' })
          const rules = {
            username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
          }
          return { args: { visible: true }, state: { visible, form, rules } }
        } }
      </template>
      <template #default="{ state }">
        <ModalForm
          :visible="state.visible.value"
          title="新建用户"
          :model="state.form"
          :rules="state.rules"
          submit-text="创建"
          @update:visible="state.visible = $event"
          @submit="state.visible = false"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="state.form.username" placeholder="登录用户名" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="state.form.email" placeholder="email@example.com" />
          </el-form-item>
          <el-form-item label="角色" prop="role">
            <el-select v-model="state.form.role" placeholder="选择角色" style="width: 100%">
              <el-option label="管理员" value="admin" />
              <el-option label="普通用户" value="user" />
            </el-select>
          </el-form-item>
        </ModalForm>
      </template>
    </Variant>

    <!-- 窄表单(密码重置场景) -->
    <Variant title="窄表单(密码重置)">
      <template #setup>
        { () => {
          const visible = ref(true)
          const form = ref({ password: '' })
          return { args: { visible: true }, state: { visible, form } }
        } }
      </template>
      <template #default="{ state }">
        <ModalForm
          :visible="state.visible.value"
          title="重置密码"
          :width="420"
          :model="state.form"
          @update:visible="state.visible = $event"
          @submit="state.visible = false"
        >
          <el-form-item label="新密码">
            <el-input v-model="state.form.password" type="password" show-password />
          </el-form-item>
        </ModalForm>
      </template>
    </Variant>

    <!-- 加载中 -->
    <Variant title="加载中" :init-state="() => ({ visible: true, loading: true })">
      <template #default="{ state }">
        <ModalForm
          :visible="state.visible"
          :loading="state.loading"
          title="提交中"
          :model="{ name: '不可编辑' }"
        >
          <el-form-item label="名称">
            <el-input model-value="不可编辑" disabled />
          </el-form-item>
        </ModalForm>
      </template>
    </Variant>
  </Story>
</template>