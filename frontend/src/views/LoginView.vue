<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { KeyRound, Mail, Send } from 'lucide-vue-next'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const mode = ref('password')
const busy = ref(false)
const sending = ref(false)
const error = ref('')
const notice = ref('')
const mailEnabled = ref(false)
const statusLoading = ref(true)
const countdown = ref(0)
const passwordForm = reactive({ username: '', password: '' })
const emailForm = reactive({ email: '', code: '' })
let timer

onMounted(async () => {
  try {
    const status = await api.emailStatus()
    mailEnabled.value = Boolean(status.enabled)
  } catch {
    mailEnabled.value = false
  } finally {
    statusLoading.value = false
  }
})

onBeforeUnmount(() => clearInterval(timer))

function switchMode(next) {
  if (next === 'email' && !mailEnabled.value) return
  mode.value = next
  error.value = ''
  notice.value = ''
}

function startCountdown(seconds = 60) {
  clearInterval(timer)
  countdown.value = seconds
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function sendCode() {
  if (!emailForm.email) {
    error.value = '请先输入用于登录的邮箱地址'
    return
  }
  sending.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await api.sendEmailCode(emailForm.email, 'LOGIN')
    notice.value = result.message || '验证码已发送，请检查收件箱和垃圾邮件目录'
    startCountdown(result.resendIn || 60)
  } catch (e) {
    error.value = e.message
  } finally {
    sending.value = false
  }
}

async function submit() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (mode.value === 'email') await auth.emailLogin(emailForm)
    else await auth.login(passwordForm)
    router.push('/')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="account-page">
    <form class="account-card account-card-wide" @submit.prevent="submit">
      <small>PLAYER ACCESS</small>
      <h1>进入弈境</h1>
      <p>使用账号密码，或通过已绑定邮箱接收一次性验证码登录。</p>

      <div class="account-tabs" role="tablist" aria-label="登录方式">
        <button
          type="button"
          :class="{ active: mode === 'password' }"
          role="tab"
          :aria-selected="mode === 'password'"
          @click="switchMode('password')"
        >
          <KeyRound :size="17" /> 密码登录
        </button>
        <button
          type="button"
          :class="{ active: mode === 'email' }"
          role="tab"
          :aria-selected="mode === 'email'"
          :disabled="statusLoading || !mailEnabled"
          @click="switchMode('email')"
        >
          <Mail :size="17" /> 邮箱验证码
        </button>
      </div>

      <template v-if="mode === 'password'">
        <label>用户名<input v-model.trim="passwordForm.username" required autocomplete="username" /></label>
        <label>密码<input v-model="passwordForm.password" type="password" required autocomplete="current-password" /></label>
      </template>
      <template v-else>
        <label>绑定邮箱<input v-model.trim="emailForm.email" type="email" required autocomplete="email" placeholder="name@example.com" /></label>
        <label>邮箱验证码
          <span class="code-field">
            <input v-model.trim="emailForm.code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="\d{6}" required placeholder="6 位验证码" />
            <button type="button" class="code-button" :disabled="sending || countdown > 0" @click="sendCode">
              <Send :size="15" /> {{ countdown > 0 ? `${countdown}s` : (sending ? '发送中' : '获取验证码') }}
            </button>
          </span>
        </label>
      </template>

      <div v-if="!statusLoading && !mailEnabled" class="mail-disabled-note">
        邮箱验证码登录暂未开放。管理员可在“管理后台 → 邮件配置”中设置 SMTP 并启用。
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="form-notice" role="status">{{ notice }}</p>
      <button class="primary-button account-submit" :disabled="busy">
        {{ busy ? '正在验证…' : (mode === 'email' ? '验证并登录' : '登录') }}
      </button>
      <router-link to="/register">还没有账号？立即注册</router-link>
      <em>演示管理员：admin / admin123</em>
    </form>
  </div>
</template>
