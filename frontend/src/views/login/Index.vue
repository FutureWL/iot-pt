<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, OfficeBuilding } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456',
  tenantCode: 'default'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }]
}

async function onLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form)
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/dashboard'
      router.push(redirect)
    } catch (e) {
      // 拦截器已提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <div class="login-bg" />
    <div class="login-card">
      <div class="login-left">
        <div class="login-brand">
          <el-icon :size="48" color="#fff"><Connection /></el-icon>
          <h1>物联网平台</h1>
          <p>通用设备接入 · 物模型 · 实时数据 · 规则引擎</p>
        </div>
      </div>
      <div class="login-right">
        <h2 class="login-title">欢迎登录</h2>
        <p class="login-subtitle">输入您的账号信息以继续</p>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="onLogin"
        >
          <el-form-item prop="tenantCode">
            <el-input v-model="form.tenantCode" placeholder="租户编码" :prefix-icon="OfficeBuilding" />
          </el-form-item>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="onLogin"
          >
            登 录
          </el-button>
          <p class="login-tip">默认账号: admin / 123456</p>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  position: relative;
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #1e3a8a 0%, #0f766e 50%, #047857 100%);
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(circle at 20% 30%, rgba(64,158,255,0.3) 0%, transparent 50%),
                radial-gradient(circle at 80% 70%, rgba(103,194,58,0.3) 0%, transparent 50%);
  }
}

.login-card {
  position: relative;
  display: flex;
  width: 880px;
  max-width: calc(100vw - 32px);
  height: 520px;
  max-height: calc(100vh - 32px);
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-left {
  flex: 1;
  background: linear-gradient(160deg, #1e3a8a 0%, #0c4a6e 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  padding: 32px;
  position: relative;
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(circle at 30% 50%, rgba(64,158,255,0.4) 0%, transparent 60%);
  }
}

.login-brand {
  position: relative;
  text-align: center;
  h1 {
    font-size: 32px;
    margin: 16px 0 8px;
    font-weight: 600;
  }
  p {
    font-size: 14px;
    opacity: 0.85;
    line-height: 1.6;
  }
}

.login-right {
  flex: 1;
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-title {
  font-size: 24px;
  margin: 0 0 8px;
  color: #303133;
}

.login-subtitle {
  color: #909399;
  margin: 0 0 32px;
  font-size: 14px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
}

.login-tip {
  margin-top: 16px;
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
}

@media (max-width: 768px) {
  .login-card {
    width: calc(100vw - 32px);
    height: auto;
  }
  .login-left {
    display: none;
  }
  .login-right {
    padding: 32px 24px;
  }
  .login-title { font-size: 20px; }
}
</style>
