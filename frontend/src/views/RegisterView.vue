<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Send } from 'lucide-vue-next'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const busy = ref(false)
const sending = ref(false)
const error = ref('')
const notice = ref('')
const mailEnabled = ref(false)
const statusLoading = ref(true)
const countdown = ref(0)
const form = reactive({ username: '', displayName: '', password: '', email: '', code: '' })
let timer

onMounted(async () => {
  try {
    const status = await api.emailStatus()
    mailEnabled.value = Boolean(status.enabled)
  } catch (e) {
    error.value = e.message
  } finally {
    statusLoading.value = false
  }
})

onBeforeUnmount(() => clearInterval(timer))

function startCountdown(seconds = 60) {
  clearInterval(timer)
  countdown.value = seconds
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function sendCode() {
  if (!form.email) {
    error.value = '请先输入接收验证码的邮箱地址'
    return
  }
  sending.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await api.sendEmailCode(form.email, 'REGISTER')
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
    const payload = {
      username: form.username,
      displayName: form.displayName,
      password: form.password,
      ...(mailEnabled.value ? { email: form.email, code: form.code } : {})
    }
    await auth.register(payload)
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
      <small>NEW CHALLENGER</small>
      <h1>创建执棋账号</h1>
      <p>{{ mailEnabled ? '验证邮箱后即可注册，并可使用邮箱验证码快捷登录。' : '创建账号后即可保存档案、参与天梯并记录赛季积分。' }}</p>
      <label>用户名<input v-model.trim="form.username" minlength="3" maxlength="24" required autocomplete="username" /></label>
      <label>显示名称<input v-model.trim="form.displayName" minlength="2" maxlength="20" required autocomplete="nickname" /></label>
      <label>密码<input v-model="form.password" type="password" minlength="6" maxlength="72" required autocomplete="new-password" /></label>
      <template v-if="mailEnabled">
        <label>邮箱<input v-model.trim="form.email" type="email" required autocomplete="email" placeholder="用于接收验证码和登录" /></label>
        <label>邮箱验证码
          <span class="code-field">
            <input v-model.trim="form.code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="\d{6}" required placeholder="6 位验证码" />
            <button type="button" class="code-button" :disabled="sending || countdown > 0" @click="sendCode">
              <Send :size="15" /> {{ countdown > 0 ? `${countdown}s` : (sending ? '发送中' : '获取验证码') }}
            </button>
          </span>
        </label>
      </template>
      <div v-else-if="!statusLoading" class="mail-disabled-note compact">当前未启用邮箱验证，注册后仍可使用用户名和密码登录。</div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="form-notice" role="status">{{ notice }}</p>
      <button class="primary-button account-submit" :disabled="busy || statusLoading">{{ busy ? '正在创建…' : '创建账号' }}</button>
      <router-link to="/login">已有账号？返回登录</router-link>
    </form>
  </div>
</template>
