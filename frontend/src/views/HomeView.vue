<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Swords, Sparkles, ShieldCheck, Timer, Map, ChevronRight, Play, Users, Bot, Trophy, GraduationCap, Puzzle, BookOpen } from 'lucide-vue-next'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const creating = ref(false)
const error = ref('')
const boardSize = ref(3)
const aiDifficulty = ref('normal')
const deckArchetype = ref('balanced')
const roomId = ref('')
const waiting = ref(false)
const cooldownRemaining = ref(0)
const archetypes = ref([])
const boardMotion = reactive({ tiltX: '0deg', tiltY: '0deg', glowX: '50%', glowY: '46%' })
let poller
let cooldownTimer

async function start(mode = 'AI', extra = {}) {
  if (typeof mode !== 'string') mode = 'AI'
  if (cooldownRemaining.value > 0) return showCooldownError()
  creating.value = true
  error.value = ''
  try {
    const match = await api.createMatch('', {
      mode,
      boardSize: extra.boardSize ?? boardSize.value,
      aiDifficulty: extra.aiDifficulty ?? aiDifficulty.value,
      scenario: extra.scenario || 'standard',
      deckArchetype: extra.deckArchetype ?? deckArchetype.value,
      puzzleId: extra.puzzleId || null
    })
    localStorage.setItem(`fieldrealm-player-${match.id}`, 'p1')
    router.push(`/battle/${match.id}`)
  } catch (e) {
    error.value = e.message
  } finally {
    creating.value = false
  }
}

async function joinRoom() {
  if (cooldownRemaining.value > 0) return showCooldownError()
  if (!roomId.value.trim()) return
  creating.value = true
  try {
    const match = await api.joinMatch(roomId.value.trim())
    localStorage.setItem(`fieldrealm-player-${match.id}`, 'p2')
    router.push(`/battle/${match.id}`)
  } catch (e) {
    error.value = e.message
  } finally {
    creating.value = false
  }
}

async function ranked() {
  if (cooldownRemaining.value > 0) return showCooldownError()
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  creating.value = true
  error.value = ''
  try {
    const state = await api.queueRanked(boardSize.value)
    handleQueue(state)
    if (state.status === 'WAITING') {
      waiting.value = true
      poller = setInterval(async () => handleQueue(await api.queueStatus()), 1200)
    }
  } catch (e) {
    error.value = e.message
  } finally {
    creating.value = false
  }
}

function handleQueue(state) {
  if (state.status === 'MATCHED') {
    clearInterval(poller)
    waiting.value = false
    localStorage.setItem(`fieldrealm-player-${state.matchId}`, state.playerId)
    router.push(`/battle/${state.matchId}`)
  }
}

async function cancel() {
  clearInterval(poller)
  waiting.value = false
  await api.cancelQueue()
}

function refreshCooldown() {
  const until = Number(localStorage.getItem('fieldrealm-match-ban-until') || 0)
  cooldownRemaining.value = Math.max(0, Math.ceil((until - Date.now()) / 1000))
  if (!cooldownRemaining.value && until) localStorage.removeItem('fieldrealm-match-ban-until')
}

function showCooldownError() {
  error.value = `退出惩罚中，还需等待 ${cooldownRemaining.value} 秒才能开始新对局`
}

onMounted(async () => {
  refreshCooldown()
  cooldownTimer = window.setInterval(refreshCooldown, 500)
  try { archetypes.value = await api.archetypes() } catch { /* optional */ }
  if (auth.isLoggedIn) {
    try {
      const state = await api.matchCooldown()
      if (state.remainingSeconds > cooldownRemaining.value) {
        localStorage.setItem('fieldrealm-match-ban-until', String(Date.now() + state.remainingSeconds * 1000))
        refreshCooldown()
      }
    } catch {}
  }
})

function moveBoard(event) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const rect = event.currentTarget.getBoundingClientRect()
  const x = Math.min(1, Math.max(-1, ((event.clientX - rect.left) / rect.width - 0.5) * 2))
  const y = Math.min(1, Math.max(-1, ((event.clientY - rect.top) / rect.height - 0.5) * 2))
  boardMotion.tiltX = `${(x * 3.2).toFixed(2)}deg`
  boardMotion.tiltY = `${(-y * 2.2).toFixed(2)}deg`
  boardMotion.glowX = `${((x + 1) * 50).toFixed(1)}%`
  boardMotion.glowY = `${((y + 1) * 50).toFixed(1)}%`
}

function resetBoard() {
  boardMotion.tiltX = '0deg'
  boardMotion.tiltY = '0deg'
  boardMotion.glowX = '50%'
  boardMotion.glowY = '46%'
}

onBeforeUnmount(() => { clearInterval(poller); clearInterval(cooldownTimer) })
</script>

<template>
  <div class="home-page">
    <section class="hero section-wrap">
      <div class="hero-ambient" aria-hidden="true"><i></i><i></i><i></i></div>
      <div class="hero-copy">
        <span class="eyebrow"><Sparkles :size="15" /> 雾海纪元 · 第一赛季</span>
        <h1>胜负不在伤害，<br /><em>而在场地的归属。</em></h1>
        <p>部署场地、驻扎单位、发动射程争夺。没有血量与消灭，只有不断改写的域面主动权——绝杀或积分，两条胜利之路。</p>
        <div class="hero-actions">
          <button class="primary-button" :disabled="creating || cooldownRemaining > 0" @click="start('AI')"><Play :size="18" fill="currentColor" />{{ creating ? '正在开启弈境…' : '开始人机对弈' }}</button>
          <button class="secondary-button" :disabled="creating || cooldownRemaining > 0" @click="start('AI', { scenario: 'tutorial', boardSize: 3, aiDifficulty: 'easy' })"><GraduationCap :size="18" />完整新手教程</button>
          <router-link to="/rules" class="secondary-button"><Map :size="18" />完整规则</router-link>
        </div>
        <p v-if="cooldownRemaining" class="cooldown-notice"><Timer :size="16"/>退出惩罚中 · {{ cooldownRemaining }} 秒后可开始新对局</p>
        <p v-if="error" class="inline-error">{{ error }}</p>
        <div class="hero-stats">
          <span><b>3×3～5×5</b><small>可变域面</small></span>
          <span><b>=边长</b><small>每回合灵力</small></span>
          <span><b>×3</b><small>总回合数</small></span>
          <span><b>2</b><small>胜利路线</small></span>
        </div>
      </div>
      <div
        class="hero-board"
        :style="{ '--tilt-x': boardMotion.tiltX, '--tilt-y': boardMotion.tiltY, '--glow-x': boardMotion.glowX, '--glow-y': boardMotion.glowY }"
        aria-label="场地示意图"
        @pointermove="moveBoard"
        @pointerleave="resetBoard"
      >
        <span class="realm-mote mote-a" aria-hidden="true">✦</span>
        <span class="realm-mote mote-b" aria-hidden="true">◇</span>
        <span class="realm-mote mote-c" aria-hidden="true">·</span>
        <span class="board-orbit orbit-a"></span>
        <span class="board-orbit orbit-b"></span>
        <div class="mini-site site-a"><i>◇</i><b>苍翠庭院</b><small>己方</small></div>
        <div class="mini-site site-b enemy"><i>◇</i><b>玄岩壁垒</b><small>敌方</small></div>
        <div class="mini-site site-core"><i>✦</i><b>灵脉核心</b><small>积分 ×2</small></div>
        <div class="mini-site site-c enemy"><i>◇</i><b>镜潮回廊</b><small>敌方</small></div>
        <div class="mini-site site-d"><i>◇</i><b>赤焰锻场</b><small>己方</small></div>
        <div class="floating-card card-left"><span>2</span><i>✦</i><b>逐风决斗家</b></div>
        <div class="floating-card card-right"><span>1</span><i>⌁</i><b>灵潮骤升</b></div>
      </div>
    </section>

    <section class="feature-strip">
      <div class="section-wrap feature-grid">
        <article><span><Swords /></span><div><b>无血量策略对抗</b><p>邻接协同、夹击与射程，让棋盘几何成为决策核心。</p></div></article>
        <article><span><ShieldCheck /></span><div><b>双重胜利逻辑</b><p>控制过半场地连结算两次触发绝杀，或终局比拼积分。</p></div></article>
        <article><span><Timer /></span><div><b>10–15 分钟一局</b><p>人机 90 秒/阶段，排位 60 秒；灵力不留存，节奏紧凑。</p></div></article>
      </div>
    </section>

    <section class="section-wrap mode-section">
      <div class="section-heading">
        <span>选择你的道路</span>
        <h2>3×3、4×4、5×5 三种弈境</h2>
        <p>灵力 = 棋盘边长；总回合 = 边长 × 3。默认推荐 3×3，4×4/5×5 为进阶规格。</p>
      </div>
      <div class="board-size-picker">
        <button v-for="n in [3, 4, 5]" :key="n" :class="{ active: boardSize === n }" @click="boardSize = n">
          {{ n }}×{{ n }}
          <small>{{ n === 3 ? '推荐' : n === 4 ? '进阶' : '史诗' }}</small>
        </button>
      </div>
      <div class="home-options-row">
        <label>AI 难度
          <select v-model="aiDifficulty">
            <option value="easy">入门</option>
            <option value="normal">标准</option>
            <option value="hard">困难</option>
          </select>
        </label>
        <label>主题卡组
          <select v-model="deckArchetype">
            <option value="balanced">均衡初阵</option>
            <option value="bastion">壁垒龟缩</option>
            <option value="ranger">游猎射程</option>
            <option value="forge">锻场突击</option>
            <option value="draw">运营过牌</option>
            <option value="dominion">绝杀快控</option>
          </select>
        </label>
      </div>
      <div class="mode-grid expanded">
        <article class="mode-card featured">
          <div class="mode-icon"><Bot /></div>
          <span class="mode-tag">单人训练</span>
          <h3>秘境试炼</h3>
          <p>按所选难度与卡组对战 AI，练习邻接、调防、绝杀节奏。</p>
          <button :disabled="creating || cooldownRemaining > 0" @click="start('AI')">立即挑战 <ChevronRight /></button>
        </article>
        <article class="mode-card room-mode">
          <div class="mode-icon"><Users /></div>
          <span class="mode-tag">实时联机</span>
          <h3>好友房间</h3>
          <p>创建房间后把 8 位房间号发给朋友，双方通过 WebSocket 实时同步。</p>
          <div class="room-actions">
            <button class="create-room-button" :disabled="creating || cooldownRemaining > 0" @click="start('PVP')">创建房间</button>
            <div class="room-join">
              <input v-model="roomId" maxlength="8" placeholder="输入房间号" />
              <button :disabled="creating || cooldownRemaining > 0" @click="joinRoom">加入</button>
            </div>
          </div>
        </article>
        <article class="mode-card ranked">
          <div class="mode-icon"><Trophy /></div>
          <span class="mode-tag">赛季排位</span>
          <h3>天梯匹配</h3>
          <p>按棋盘尺寸匹配真人对手，胜负写入积分与排行榜（60 秒阶段）。</p>
          <button v-if="!waiting" :disabled="creating || cooldownRemaining > 0" @click="ranked">开始匹配</button>
          <button v-else @click="cancel">匹配中… 点击取消</button>
        </article>
      </div>

      <div class="extra-modes">
        <button class="extra-mode" :disabled="creating" @click="start('AI', { scenario: 'tutorial', boardSize: 3, aiDifficulty: 'easy' })">
          <GraduationCap :size="18" /><span><b>新手教程</b><small>教习讲解 + 四次实操 · 约10分钟</small></span>
        </button>
        <button class="extra-mode" :disabled="creating" @click="start('AI', { scenario: 'puzzle', puzzleId: 'core-break', boardSize: 3 })">
          <Puzzle :size="18" /><span><b>残局：核心突破</b><small>3 灵力夺取天元</small></span>
        </button>
        <router-link class="extra-mode" to="/deck">
          <BookOpen :size="18" /><span><b>主题卡组</b><small>五流派构筑入口</small></span>
        </router-link>
      </div>
      <p v-if="error" class="inline-error">{{ error }}</p>
    </section>
  </div>
</template>
