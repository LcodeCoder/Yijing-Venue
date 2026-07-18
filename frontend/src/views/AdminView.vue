<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { AtSign, Library, Mail, Pencil, Plus, RefreshCw, Save, Search, Send, ShieldCheck, Trash2, Users, X } from 'lucide-vue-next'
import { api } from '../services/api'

const activeTab = ref('users')
const cards = ref([])
const users = ref([])
const query = ref('')
const type = ref('ALL')
const userQuery = ref('')
const roleFilter = ref('ALL')
const editing = ref(false)
const loadingUsers = ref(false)
const loadingMail = ref(false)
const savingMail = ref(false)
const testingMail = ref(false)
const error = ref('')
const notice = ref('')
const testEmail = ref('')
const originalId = ref('')

const emptyCard = () => ({
  id: '', name: '', type: 'SITE', rarity: 'C', cost: 1, power: 0, guard: 1,
  points: 1, effectCode: 'CUSTOM', effect: '', flavor: '', tags: []
})
const form = reactive(emptyCard())
const mail = reactive({
  enabled: false,
  configured: false,
  host: 'smtp.qq.com',
  port: 465,
  ssl: true,
  username: '',
  password: '',
  passwordConfigured: false,
  fromName: '场地弈境',
  providerHint: ''
})

const filteredCards = computed(() => cards.value.filter(card => {
  const matchesType = type.value === 'ALL' || card.type === type.value
  const keyword = `${card.name}${card.id}${card.effect}`.toLowerCase()
  return matchesType && keyword.includes(query.value.toLowerCase())
}))

const filteredUsers = computed(() => users.value.filter(user => {
  const matchesRole = roleFilter.value === 'ALL' || user.role === roleFilter.value
  const keyword = `${user.username || ''}${user.displayName || ''}${user.email || ''}${user.title || ''}`.toLowerCase()
  return matchesRole && keyword.includes(userQuery.value.toLowerCase())
}))

const boundEmailCount = computed(() => users.value.filter(user => user.email).length)
const playerCount = computed(() => users.value.filter(user => user.role !== 'ADMIN').length)
const mailReady = computed(() => Boolean(mail.configured || (mail.host && mail.username && (mail.passwordConfigured || mail.password))))

function clearFeedback() {
  error.value = ''
  notice.value = ''
}

async function loadCards() {
  try {
    cards.value = await api.adminCards()
  } catch (e) {
    error.value = e.message
  }
}

async function loadUsers() {
  loadingUsers.value = true
  try {
    users.value = await api.adminUsers()
  } catch (e) {
    error.value = e.message
  } finally {
    loadingUsers.value = false
  }
}

async function loadMail() {
  loadingMail.value = true
  try {
    const data = await api.adminMail()
    Object.assign(mail, data, { password: '' })
  } catch (e) {
    error.value = e.message
  } finally {
    loadingMail.value = false
  }
}

async function loadAll() {
  clearFeedback()
  await Promise.all([loadCards(), loadUsers(), loadMail()])
}

function openCard(card) {
  Object.assign(form, card ? JSON.parse(JSON.stringify(card)) : emptyCard())
  originalId.value = card?.id || ''
  editing.value = true
  error.value = ''
}

async function saveCard() {
  try {
    const payload = {
      ...form,
      tags: Array.isArray(form.tags) ? form.tags : String(form.tags || '').split(/[,，]/).filter(Boolean)
    }
    if (originalId.value) await api.updateCard(originalId.value, payload)
    else await api.createCard(payload)
    notice.value = '卡牌目录已保存'
    editing.value = false
    await loadCards()
  } catch (e) {
    error.value = e.message
  }
}

async function removeCard(card) {
  if (!confirm(`确认删除「${card.name}」？`)) return
  try {
    await api.deleteCard(card.id)
    notice.value = `已删除「${card.name}」`
    await loadCards()
  } catch (e) {
    error.value = e.message
  }
}

function applyMailResponse(data) {
  Object.assign(mail, data, { password: '' })
}

function mailPayload() {
  return {
    enabled: mail.enabled,
    host: mail.host,
    port: Number(mail.port),
    ssl: mail.ssl,
    username: mail.username,
    password: mail.password,
    fromName: mail.fromName
  }
}

async function saveMail() {
  savingMail.value = true
  clearFeedback()
  try {
    const data = await api.saveAdminMail(mailPayload())
    applyMailResponse(data)
    notice.value = mail.enabled ? '邮件配置已保存，邮箱验证码登录已开放' : '邮件配置已保存，邮箱验证码登录当前关闭'
  } catch (e) {
    error.value = e.message
  } finally {
    savingMail.value = false
  }
}

async function testMailChannel() {
  if (!testEmail.value) {
    error.value = '请输入接收测试邮件的邮箱地址'
    return
  }
  testingMail.value = true
  clearFeedback()
  try {
    const data = await api.saveAdminMail(mailPayload())
    applyMailResponse(data)
    const result = await api.testAdminMail(testEmail.value)
    notice.value = result.message || '测试邮件已发送'
  } catch (e) {
    error.value = e.message
  } finally {
    testingMail.value = false
  }
}

function formatDate(value) {
  if (!value) return '—'
  const numeric = typeof value === 'number' ? (value > 1e12 ? value : value * 1000) : value
  const date = new Date(numeric)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
}

onMounted(loadAll)
</script>

<template>
  <div class="admin-page section-wrap">
    <header class="admin-hero admin-hero-dashboard">
      <div>
        <small>FIELD REALM CONTROL</small>
        <h1>弈境管理后台</h1>
        <p>查看注册玩家、维护全服卡牌，并配置邮箱验证码登录通道。</p>
      </div>
      <button class="secondary-button admin-refresh" :disabled="loadingUsers || loadingMail" @click="loadAll">
        <RefreshCw :class="{ spinning: loadingUsers || loadingMail }" :size="17" /> 刷新数据
      </button>
    </header>

    <section class="admin-summary" aria-label="后台概览">
      <article><span><Users /></span><div><small>注册用户</small><b>{{ users.length }}</b><p>{{ playerCount }} 位玩家</p></div></article>
      <article><span><AtSign /></span><div><small>已绑邮箱</small><b>{{ boundEmailCount }}</b><p>可使用验证码登录</p></div></article>
      <article><span><Library /></span><div><small>卡牌目录</small><b>{{ cards.length }}</b><p>当前全服卡池</p></div></article>
      <article :class="{ ready: mail.enabled && mailReady }"><span><ShieldCheck /></span><div><small>邮件通道</small><b>{{ mail.enabled && mailReady ? '已启用' : '未启用' }}</b><p>{{ mail.host || '尚未配置 SMTP' }}</p></div></article>
    </section>

    <nav class="admin-tabs" aria-label="管理模块">
      <button :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'; clearFeedback()"><Users />用户列表</button>
      <button :class="{ active: activeTab === 'cards' }" @click="activeTab = 'cards'; clearFeedback()"><Library />卡牌管理</button>
      <button :class="{ active: activeTab === 'mail' }" @click="activeTab = 'mail'; clearFeedback()"><Mail />邮件配置</button>
    </nav>

    <p v-if="error && !editing" class="form-error admin-feedback" role="alert">{{ error }}</p>
    <p v-if="notice && !editing" class="form-notice admin-feedback" role="status">{{ notice }}</p>

    <section v-if="activeTab === 'users'" class="admin-panel">
      <header class="admin-panel-heading">
        <div><small>USER DIRECTORY</small><h2>用户列表</h2><p>查看账号、邮箱绑定状态与赛季数据。</p></div>
        <span>{{ filteredUsers.length }} / {{ users.length }} 位</span>
      </header>
      <div class="admin-toolbar">
        <label><Search /><input v-model="userQuery" placeholder="搜索用户名、昵称或邮箱" /></label>
        <select v-model="roleFilter" aria-label="按角色筛选">
          <option value="ALL">全部角色</option>
          <option value="USER">普通用户</option>
          <option value="ADMIN">管理员</option>
        </select>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-user-table">
          <thead><tr><th>用户</th><th>邮箱</th><th>角色</th><th>天梯积分</th><th>战绩</th><th>胜率</th><th>注册日期</th></tr></thead>
          <tbody>
            <tr v-if="loadingUsers"><td colspan="7" class="table-state">正在读取用户数据…</td></tr>
            <tr v-else-if="!filteredUsers.length"><td colspan="7" class="table-state">没有符合条件的用户</td></tr>
            <template v-else>
              <tr v-for="user in filteredUsers" :key="user.id">
                <td><div class="admin-user-cell"><span>{{ user.avatar || (user.displayName || user.username || '?').slice(0, 1) }}</span><div><strong>{{ user.displayName || user.username }}</strong><small>@{{ user.username }}</small></div></div></td>
                <td><span :class="['email-state', { empty: !user.email }]">{{ user.email || '未绑定' }}</span></td>
                <td><span :class="['role-badge', user.role?.toLowerCase()]">{{ user.role === 'ADMIN' ? '管理员' : '玩家' }}</span></td>
                <td><b class="rating-value">{{ user.rating }}</b><small class="tier-copy">{{ user.rank }}</small></td>
                <td>{{ user.wins }} 胜 / {{ user.games }} 局</td>
                <td>{{ user.winRate || 0 }}%</td>
                <td>{{ formatDate(user.createdAt) }}</td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>

    <section v-else-if="activeTab === 'cards'" class="admin-panel">
      <header class="admin-panel-heading with-action">
        <div><small>CARD CATALOG</small><h2>卡牌管理</h2><p>新增卡牌会自动进入可用卡池，无需修改代码。</p></div>
        <button class="primary-button" @click="openCard()"><Plus />新增卡牌</button>
      </header>
      <div class="admin-toolbar">
        <label><Search /><input v-model="query" placeholder="搜索名称、ID、效果" /></label>
        <select v-model="type" aria-label="按卡牌类型筛选"><option value="ALL">全部类型</option><option>SITE</option><option>UNIT</option><option>SPELL</option><option>SECRET</option></select>
        <span>共 {{ filteredCards.length }} 张</span>
      </div>
      <div class="admin-card-grid">
        <article v-for="card in filteredCards" :key="card.id" class="admin-card">
          <div><span :class="['type-chip', card.type.toLowerCase()]">{{ card.type }}</span><b>{{ card.rarity }}</b></div>
          <h3>{{ card.name }}</h3><code>{{ card.id }}</code><p>{{ card.effect }}</p>
          <dl><div><dt>费用</dt><dd>{{ card.cost }}</dd></div><div><dt>战力</dt><dd>{{ card.power }}</dd></div><div><dt>守力</dt><dd>{{ card.guard }}</dd></div><div><dt>积分</dt><dd>{{ card.points }}</dd></div></dl>
          <footer><button @click="openCard(card)"><Pencil />编辑</button><button class="danger" @click="removeCard(card)"><Trash2 />删除</button></footer>
        </article>
      </div>
    </section>

    <section v-else class="admin-panel mail-panel">
      <header class="admin-panel-heading">
        <div><small>SMTP DELIVERY</small><h2>邮件验证码配置</h2><p>逻辑参考 resume-lcode：通过 SMTP 发送 6 位验证码，5 分钟有效，验证成功后一次性失效。</p></div>
        <span :class="['mail-status-pill', { active: mail.enabled && mailReady }]">{{ mail.enabled && mailReady ? '通道运行中' : '通道未启用' }}</span>
      </header>

      <form class="mail-settings-form" @submit.prevent="saveMail">
        <div class="mail-enable-row">
          <div><strong>启用邮箱验证码登录</strong><p>启用后，新用户注册必须验证邮箱；已绑定邮箱的用户可免密码登录。</p></div>
          <label class="toggle-control"><input v-model="mail.enabled" type="checkbox" /><span></span></label>
        </div>

        <div class="mail-form-grid">
          <label>SMTP 服务器<input v-model.trim="mail.host" required placeholder="smtp.qq.com" /></label>
          <label>端口<input v-model.number="mail.port" type="number" min="1" max="65535" required /></label>
          <label class="wide">发件邮箱<input v-model.trim="mail.username" type="email" placeholder="your-account@qq.com" autocomplete="off" /></label>
          <label class="wide">SMTP 授权码
            <input v-model="mail.password" type="password" autocomplete="new-password" :placeholder="mail.passwordConfigured ? '已保存；留空表示不修改' : '请输入邮箱服务商生成的 SMTP 授权码'" />
          </label>
          <label>发件名称<input v-model.trim="mail.fromName" required placeholder="场地弈境" /></label>
          <label class="ssl-option"><span>连接安全</span><span class="check-line"><input v-model="mail.ssl" type="checkbox" /> 使用 SSL（QQ 邮箱 465 端口推荐）</span></label>
        </div>

        <aside class="mail-config-note">
          <Mail :size="19" />
          <div><strong>QQ 邮箱配置提示</strong><p>{{ mail.providerHint || '在 QQ 邮箱设置中开启 SMTP 服务，并使用生成的授权码，不要填写邮箱登录密码。' }}</p></div>
        </aside>

        <div class="mail-actions">
          <button class="primary-button" :disabled="savingMail"><Save :size="17" />{{ savingMail ? '保存中…' : '保存配置' }}</button>
          <div class="mail-test-box">
            <input v-model.trim="testEmail" type="email" placeholder="接收测试邮件的地址" aria-label="测试收件邮箱" />
            <button type="button" class="secondary-button" :disabled="testingMail" @click="testMailChannel"><Send :size="16" />{{ testingMail ? '发送中…' : '保存并测试' }}</button>
          </div>
        </div>
      </form>
    </section>

    <div v-if="editing" class="modal-backdrop" @click.self="editing = false">
      <form class="card-editor" @submit.prevent="saveCard">
        <header><div><small>CARD EDITOR</small><h2>{{ originalId ? '编辑卡牌' : '新增卡牌' }}</h2></div><button type="button" class="icon-button" @click="editing = false"><X /></button></header>
        <div class="editor-grid">
          <label>ID<input v-model.trim="form.id" :disabled="!!originalId" required /></label><label>名称<input v-model.trim="form.name" required /></label>
          <label>类型<select v-model="form.type"><option>SITE</option><option>UNIT</option><option>SPELL</option><option>SECRET</option></select></label>
          <label>稀有度<select v-model="form.rarity"><option>C</option><option>R</option><option>SR</option><option>SSR</option></select></label>
          <label>费用<input v-model.number="form.cost" type="number" min="0" max="9" /></label><label>战力<input v-model.number="form.power" type="number" min="0" /></label>
          <label>守力<input v-model.number="form.guard" type="number" min="0" /></label><label>积分<input v-model.number="form.points" type="number" min="0" /></label>
          <label>效果代码<input v-model.trim="form.effectCode" required /></label><label class="wide">效果描述<textarea v-model.trim="form.effect"></textarea></label>
          <label class="wide">风味文字<textarea v-model.trim="form.flavor"></textarea></label>
        </div>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="primary-button"><Save />保存卡牌</button>
      </form>
    </div>
  </div>
</template>
