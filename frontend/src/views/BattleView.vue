<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { ArrowLeft, BookOpen, CheckCircle2, ChevronRight, Clock3, Dices, Hand, History, LoaderCircle, LocateFixed, LogOut, Minus, Move, Plus, RotateCcw, Sparkles, Swords, Volume2, VolumeX, X, Zap, Trash2 } from 'lucide-vue-next'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'
import GameCard from '../components/GameCard.vue'
import RealmSite from '../components/RealmSite.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const game = ref(null)
const cards = ref([])
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const selectedCard = ref(null)
const selectedUnit = ref(null)
const moveModeUnit = ref(null)
const forcedDiscarding = ref(false)
const cycleMode = ref(false)
const tutorialDismissedStep = ref('')
const tutorialVisible = ref(true)
const tutorialPageIndex = ref(0)
const tutorialAwaiting = ref('')
const showLog = ref(false)
const soundOn = ref(true)
const pulse = ref('')
const drawReveal = ref(null)
const phaseFx = ref(null)
const clock = ref(Date.now())
const combatFx = ref(null)
const placementFx = ref(null)
const sitePulseIndex = ref(null)
const aiFx = ref(null)
const opponentFxBusy = ref(false)
const initiativeFx = ref(false)
const initiativeStage = ref('ready')
const initiativePlayerRoll = ref(1)
const initiativeOpponentRoll = ref(1)
const battlefieldViewport = ref(null)
const boardZoom = ref(1)
const boardPanX = ref(0)
const boardPanY = ref(0)
const boardPanning = ref(false)
const boardInteractionActive = ref(false)
const boardSettling = ref(false)
const boardGliding = ref(false)
const boardFeedbackX = ref(0)
const boardFeedbackY = ref(0)
const showExitConfirm = ref(false)
const pendingExitTarget = ref('/')
const allowRouteLeave = ref(false)
const exiting = ref(false)
const exitError = ref('')
const BOARD_WIDTH = 1320
const BOARD_HEIGHT = 820
const activeBoardPointers = new Map()
let boardDragStart = null
let boardPinchStart = null
let blockBoardClick = false
let boardClickGuardTimer
let boardSettleTimer
let boardDragFeedback = false
let boardVelocityX = 0
let boardVelocityY = 0
let boardLastMoveAt = 0
let boardMomentumFrame
let audioContext
let boardResizeObserver
let initiativeRollTimer
let initiativeRevealTimer
let initiativeKey = ''
let client
let pulseTimer
let revealTimer
let dismissRevealTimer
let phaseTimer
let combatTimer
let placementTimer
let countdownTimer
let opponentFxProcessing = false
let pendingDrawCards = []
let opponentFxRunId = 0
const opponentFxQueue = []
const recentOpponentFxKeys = new Set()

const cardMap = computed(() => Object.fromEntries(cards.value.map(c => [c.id, c])))
const localPlayerId = computed(() => game.value?.players?.find(p => auth.user?.id && p.accountId === auth.user.id)?.id || localStorage.getItem(`fieldrealm-player-${game.value?.id}`) || 'p1')
const rivalPlayerId = computed(() => localPlayerId.value === 'p1' ? 'p2' : 'p1')
const localPlayerIndex = computed(() => game.value?.players?.findIndex(p => p.id === localPlayerId.value) ?? 0)

const player = computed(() => game.value?.players?.[localPlayerIndex.value])
const rival = computed(() => game.value?.players?.[1 - localPlayerIndex.value])
const active = computed(() => game.value?.players?.[game.value?.activePlayerIndex])
const humanTurn = computed(() => !opponentFxBusy.value && !game.value?.waitingForOpponent && active.value?.id === localPlayerId.value && game.value?.phase !== 'FINISHED')
const hand = computed(() => player.value?.hand?.map(id => cardMap.value[id]).filter(Boolean) || [])
const handOverflow = computed(() => Math.max(0, hand.value.length - 7))
const playerReady = computed(() => Boolean(game.value?.sites?.some(site => site.ownerId === localPlayerId.value)))
const rivalReady = computed(() => Boolean(game.value?.sites?.some(site => site.ownerId === rivalPlayerId.value)))
const initialDeployment = computed(() => !game.value?.initialContestResolved)
const bothInitialSitesReady = computed(() => Boolean(playerReady.value && rivalReady.value))
const dieGlyph = value => ['', '\u2680', '\u2681', '\u2682', '\u2683', '\u2684', '\u2685'][Number(value) || 1]
const contestStarter = computed(() => game.value?.players?.[game.value?.contestStarterIndex])
const boardTransform = computed(() => `translate3d(${boardPanX.value}px, ${boardPanY.value}px, 0) scale(${boardZoom.value})`)
const boardFeedbackStyle = computed(() => ({ '--drag-glow-x': `${50 - boardFeedbackX.value * 42}%`, '--drag-glow-y': `${50 - boardFeedbackY.value * 42}%`, '--drag-strength': Math.min(1, Math.hypot(boardFeedbackX.value, boardFeedbackY.value)).toFixed(2) }))
const boardZoomLabel = computed(() => `${Math.round(boardZoom.value * 100)}%`)
const maxRounds = computed(() => Math.max(3, Number(game.value?.boardSize || 3) * 3))
const roundProgress = computed(() => Math.min(100, ((Number(game.value?.turnNumber || 1) / (maxRounds.value * 2)) * 100)))
const dominationTarget = computed(() => game.value?.dominationTarget || Math.floor((game.value?.sites?.length || 9) / 2) + 1)
const playerKillProgress = computed(() => player.value?.stableTicks || 0)
const rivalKillProgress = computed(() => rival.value?.stableTicks || 0)
const countControl = id => game.value?.sites?.filter(site => site.ownerId === id).length || 0
const killThreat = computed(() => {
  if (playerKillProgress.value >= 1 && countControl(localPlayerId.value) >= dominationTarget.value) {
    return `⚠ 你方绝杀 ${playerKillProgress.value}/2：再结算一次将获胜`
  }
  if (rivalKillProgress.value >= 1 && countControl(rivalPlayerId.value) >= dominationTarget.value) {
    return `⚠ 对手绝杀 ${rivalKillProgress.value}/2：必须打断其控场`
  }
  return ''
})
const targetMode = computed(() => {
  if (forcedDiscarding.value || cycleMode.value) return ''
  if (moveModeUnit.value) return 'site'
  if (selectedCard.value) {
    if (['SITE', 'UNIT'].includes(selectedCard.value.type)) return 'site'
    if (['SEAL', 'SURGE', 'REINFORCE', 'RANGE', 'REFRESH'].includes(selectedCard.value.effectCode)) return 'unit'
  }
  if (selectedUnit.value) return 'site'
  return ''
})
const targetUnitOwner = computed(() => selectedCard.value?.effectCode === 'SEAL' ? rivalPlayerId.value : targetMode.value === 'unit' ? localPlayerId.value : '')
const instruction = computed(() => {
  if (!game.value) return ''
  if (game.value.waitingForOpponent) return `房间号 ${game.value.id} · 等待另一位玩家加入`
  if (game.value.phase === 'FINISHED') return game.value.statusText
  if (!humanTurn.value) return initialDeployment.value ? '对手正在完成初始布阵，完成后才会开启争夺' : `${rival.value?.name || '对手'}正在推演局势…`
  if (forcedDiscarding.value) return `回合结束：请从手牌中弃置 ${handOverflow.value} 张牌`
  if (cycleMode.value) return '筛牌：选择一张手牌弃掉并抽1张（每回合限1次）'
  if (moveModeUnit.value) return `调防「${moveModeUnit.value.name}」：点击相邻己方场地（耗1灵力）`
  if (initialDeployment.value && bothInitialSitesReady.value) return '双方场地已就位，先手骰将决定第一次争夺顺序'
  if (killThreat.value) return killThreat.value
  if (game.value.finalRound && game.value.phase === 'DEPLOY') return '终局回合：不可新部署场地，可调防、术式后进入争夺'
  if (selectedCard.value) {
    if (selectedCard.value.cost > player.value.energy && !(selectedCard.value.type === 'SPELL' && selectedCard.value.cost === 1 && (player.value.momentum || 0) >= 3)) {
      return '灵力不足：可弃置这张牌，或积满气势免费打1费术式'
    }
    return targetMode.value === 'unit' ? '请选择可用的目标单位' : '请选择高亮的目标场地（己方场地可覆盖改造）'
  }
  if (selectedUnit.value) {
    if (selectedUnit.value.shaken) return '该单位动摇中，本回合不可进攻'
    return `当前射程 ${selectedUnit.value.attackRange || 1}：金色高亮为可争夺目标`
  }
  if (initialDeployment.value) return playerReady.value ? '你的场地已就位，点击「完成部署」让对手布阵' : '先从手牌部署至少一张场地卡'
  return game.value.statusText
})
const phaseLabel = computed(() => ({ DEPLOY: '部署阶段', CONTEST: '争夺阶段', FINISHED: '对局结束' }[game.value?.phase] || ''))
const secondsRemaining = computed(() => {
  const endsAt = game.value?.phaseEndsAt
  if (!endsAt || game.value?.phase === 'FINISHED') return 0
  return Math.max(0, Math.ceil((new Date(endsAt).getTime() - clock.value) / 1000))
})
const phaseClockLabel = computed(() => game.value?.phase === 'FINISHED' ? '' : `${secondsRemaining.value}s`)
const phaseClockUrgent = computed(() => secondsRemaining.value > 0 && secondsRemaining.value <= 5)
const activeMatch = computed(() => Boolean(game.value && !game.value.waitingForOpponent && game.value.phase !== 'FINISHED'))
const exitPenaltyApplies = computed(() => game.value?.mode === 'PVP')
const ratingDelta = computed(() => game.value?.ratingChanges?.[localPlayerId.value] ?? 0)
const ratingAfter = computed(() => game.value?.ratingsAfter?.[localPlayerId.value])
const ratingDirection = computed(() => ratingDelta.value >= 0 ? '获得' : '减少')

watch(secondsRemaining, (next, previous) => {
  if (next > 0 && next <= 5 && next !== previous) tone('tick')
})

onBeforeRouteLeave(to => {
  if (!activeMatch.value || allowRouteLeave.value) return true
  pendingExitTarget.value = to.fullPath
  showExitConfirm.value = true
  tone('warning')
  return false
})
const actionSteps = computed(() => {
  if (game.value?.phase === 'CONTEST') return [
    { label: '选中单位', done: Boolean(selectedUnit.value) },
    { label: '点击敌方场地', done: false },
    { label: '观看争夺结算', done: false }
  ]
  if (initialDeployment.value) return [
    { label: '部署己方场地', done: playerReady.value },
    { label: '完成部署', done: false },
    { label: '双方就位后争夺', done: rivalReady.value }
  ]
  return [
    { label: '选择一张卡', done: Boolean(selectedCard.value) },
    { label: '点击目标场地', done: false },
    { label: '进入争夺', done: false }
  ]
})

onMounted(async () => {
  try {
    cards.value = await api.cards()
    if (route.params.id) game.value = await api.match(route.params.id)
    else {
      game.value = await api.createMatch()
      router.replace(`/battle/${game.value.id}`)
    }
    connect()
    if (game.value.initialContestResolved && game.value.turnNumber === 1 && game.value.playerRoll > 0) showInitiativeFx()
    window.addEventListener('keydown', shortcuts)
    window.addEventListener('beforeunload', warnBeforeUnload)
    document.addEventListener('pointerdown', handleBoardInteractionOutside)
    countdownTimer = window.setInterval(() => { clock.value = Date.now() }, 250)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
    await nextTick()
    setupBattlefieldViewport()
  }
})

onBeforeUnmount(() => {
  client?.deactivate()
  window.removeEventListener('keydown', shortcuts)
  window.removeEventListener('beforeunload', warnBeforeUnload)
  document.removeEventListener('pointerdown', handleBoardInteractionOutside)
  clearInterval(countdownTimer)
  clearTimeout(pulseTimer)
  clearTimeout(revealTimer)
  clearTimeout(dismissRevealTimer)
  clearTimeout(phaseTimer)
  clearTimeout(combatTimer)
  clearTimeout(placementTimer)
  opponentFxRunId++
  opponentFxQueue.length = 0
  pendingDrawCards = []
  clearInterval(initiativeRollTimer)
  clearTimeout(initiativeRevealTimer)
  clearTimeout(boardClickGuardTimer)
  clearTimeout(boardSettleTimer)
  cancelAnimationFrame(boardMomentumFrame)
  boardResizeObserver?.disconnect()
  activeBoardPointers.clear()
  audioContext?.close?.().catch?.(() => {})
})

function connect() {
  if (!game.value) return
  client = new Client({ webSocketFactory: () => new SockJS('/ws'), reconnectDelay: 4000, debug: () => {} })
  client.onConnect = () => client.subscribe(`/topic/matches/${game.value.id}`, message => syncGame(JSON.parse(message.body)))
  client.activate()
}

function syncGame(next) {
  const previous = game.value
  let opponentAction = null
  let newCards = []

  if (previous && next) {
    opponentAction = describeOpponentAction(previous, next)
    if (previous.phase === 'DEPLOY' && next.phase === 'CONTEST' && next.players?.[next.activePlayerIndex]?.id === localPlayerId.value) showPhaseFx('CONTEST')
    if (next.turnNumber > previous.turnNumber) {
      const oldHand = previous.players?.find(p => p.id === localPlayerId.value)?.hand || []
      newCards = addedCardIds(oldHand, next.players?.find(p => p.id === localPlayerId.value)?.hand || []).map(id => cardMap.value[id]).filter(Boolean)
    }
    const changedSite = next.sites?.find(site => {
      const old = previous.sites?.find(item => item.index === site.index)
      return old && JSON.stringify(old) !== JSON.stringify(site)
    })
    if (changedSite) flashSite(changedSite.index)
    if (previous.phase !== 'FINISHED' && next.phase === 'FINISHED') {
      tone(next.winnerId === localPlayerId.value ? 'victory' : 'defeat')
    }
  }

  const initiativeResolved = Boolean(previous && !previous.initialContestResolved && next?.initialContestResolved && next.playerRoll > 0)
  game.value = next
  if (next?.phase === 'FINISHED' || next?.players?.[next.activePlayerIndex]?.id !== localPlayerId.value) forcedDiscarding.value = false
  if (initiativeResolved) showInitiativeFx()
  if (opponentAction) enqueueOpponentFx(opponentAction)
  if (newCards.length && next.players?.[next.activePlayerIndex]?.id === localPlayerId.value) queueDrawFx(newCards)
}

function unitsWithSites(state, ownerId) {
  return state?.sites?.flatMap(site => (site.units || []).map(unit => ({
    ...unit,
    siteIndex: site.index,
    siteName: site.name,
    siteEffectCode: site.effectCode
  }))).filter(unit => unit.ownerId === ownerId) || []
}

function opponentActionKey(action) {
  return [action.kind, action.turnNumber, action.attacker?.instanceId, action.target?.index, action.detail, action.result].filter(value => value !== undefined).join(':')
}

function rememberOpponentAction(key) {
  if (!key || recentOpponentFxKeys.has(key)) return false
  recentOpponentFxKeys.add(key)
  if (recentOpponentFxKeys.size > 80) recentOpponentFxKeys.delete(recentOpponentFxKeys.values().next().value)
  return true
}

function describeOpponentAction(previous, next) {
  const opponentId = rivalPlayerId.value
  const previousActiveId = previous.players?.[previous.activePlayerIndex]?.id
  const nextActiveId = next.players?.[next.activePlayerIndex]?.id
  const opponentWasActing = previousActiveId === opponentId || nextActiveId === opponentId
  if (!opponentWasActing) return null

  const beforeUnits = unitsWithSites(previous, opponentId)
  const afterUnits = unitsWithSites(next, opponentId)
  const beforeUnitsById = new Map(beforeUnits.map(unit => [unit.instanceId, unit]))
  const attackMatch = String(next.statusText || '').match(/战力\s*(\d+)\s*vs\s*守力\s*(\d+)/i)

  if (attackMatch) {
    const attacker = afterUnits.find(unit => unit.exhausted && beforeUnitsById.has(unit.instanceId) && !beforeUnitsById.get(unit.instanceId).exhausted)
    if (attacker) {
      const targetName = String(previous.statusText || '').match(/争夺「([^」]+)」/)?.[1]
      const ownershipChange = next.sites?.find(site => {
        const oldSite = previous.sites?.find(item => item.index === site.index)
        return oldSite && oldSite.ownerId !== site.ownerId
      })
      const targetBefore = previous.sites?.find(site => site.name === targetName)
        || previous.sites?.find(site => site.index === ownershipChange?.index)
        || { index: ownershipChange?.index, name: targetName || '未知领域', baseGuard: Number(attackMatch[2]) }
      const targetAfter = next.sites?.find(site => site.index === targetBefore.index) || ownershipChange || targetBefore
      const sourceBefore = beforeUnitsById.get(attacker.instanceId) || attacker
      const captured = Boolean(targetBefore?.ownerId !== undefined && targetBefore.ownerId !== targetAfter?.ownerId)
      const result = next.statusText || '敌方争夺结算完成'
      const action = {
        kind: 'attack',
        title: '\u4e89\u593a\u573a\u5730',
        turnNumber: next.turnNumber,
        attacker: { ...attacker, siteName: sourceBefore.siteName },
        target: { ...targetBefore },
        attackPower: Number(attackMatch[1]),
        defense: Number(attackMatch[2]),
        captured,
        result,
        detail: `${attacker.name} 从「${sourceBefore.siteName}」进攻「${targetBefore.name}」`
      }
      action.key = opponentActionKey(action)
      return action
    }
  }

  const newlyDeployedUnit = afterUnits.find(unit => !beforeUnitsById.has(unit.instanceId))
  if (newlyDeployedUnit) {
    const action = {
      kind: 'deploy',
      title: '\u90e8\u7f72\u5355\u4f4d',
      turnNumber: next.turnNumber,
      detail: `${newlyDeployedUnit.name} 已部署至「${newlyDeployedUnit.siteName}」`
    }
    action.key = opponentActionKey(action)
    return action
  }

  const claimedSite = next.sites?.find(site => {
    const oldSite = previous.sites?.find(item => item.index === site.index)
    return site.ownerId === opponentId && oldSite?.ownerId !== opponentId
  })
  if (claimedSite) {
    const action = {
      kind: 'deploy',
      title: '\u90e8\u7f72\u573a\u5730',
      turnNumber: next.turnNumber,
      detail: `对手将「${claimedSite.name}」纳入布阵`
    }
    action.key = opponentActionKey(action)
    return action
  }

  const latestLog = next.log?.[0] && next.log[0] !== previous.log?.[0] ? next.log[0] : ''
  if ((next.statusText && next.statusText !== previous.statusText) || latestLog) {
    const status = String(next.statusText || '')
    const text = latestLog || status
    const attack = /\u4e89\u593a|\u6218\u529b/.test(text)
    const deploy = /\u90e8\u7f72|\u9a7b\u573a|\u5e03\u9635/.test(text)
    const spell = /\u672f\u5f0f/.test(text)
    const secret = /\u79d8\u7b56/.test(text)
    const discard = /\u5f03\u7f6e|\u5f03\u724c/.test(text)
    const turnEnd = /\u5b8c\u6210\u672c\u56de\u5408|\u56de\u5408\u7ed3\u7b97|\u81ea\u52a8\u8fdb\u5165\u4e89\u593a|\u65f6\u95f4\u8017\u5c3d/.test(text)
    const action = {
      kind: attack ? 'target' : spell ? 'spell' : secret ? 'secret' : discard ? 'discard' : deploy ? 'deploy' : turnEnd ? 'turn' : 'thinking',
      title: attack ? '\u4e89\u593a\u76ee\u6807' : spell ? '\u53d1\u52a8\u672f\u5f0f' : secret ? '\u53d1\u52a8\u79d8\u7b56' : discard ? '\u81ea\u52a8\u5f03\u724c' : deploy ? '\u5e03\u7f72\u573a\u5730' : turnEnd ? '\u7ed3\u675f\u56de\u5408' : '\u89c2\u5bdf\u5c40\u52bf',
      turnNumber: next.turnNumber,
      detail: text
    }
    action.key = opponentActionKey(action)
    return action
  }
  return null
}

function addedCardIds(previousIds, nextIds) {
  const remaining = new Map()
  previousIds.forEach(id => remaining.set(id, (remaining.get(id) || 0) + 1))
  return nextIds.filter(id => {
    const count = remaining.get(id) || 0
    if (!count) return true
    remaining.set(id, count - 1)
    return false
  })
}

function shortcuts(e) {
  if (['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target?.tagName)) return
  const boardViewShortcut = e.key === '+' || e.key === '=' || e.key === '-' || e.key === '0'
  if (boardViewShortcut && !boardInteractionActive.value) return
  if (e.key === '+' || e.key === '=') {
    e.preventDefault()
    changeBoardZoom(.12)
    return
  }
  if (e.key === '-') {
    e.preventDefault()
    changeBoardZoom(-.12)
    return
  }
  if (e.key === '0') {
    e.preventDefault()
    resetBoardView()
    return
  }
  if (e.key.toLowerCase() === 'c' && game.value?.phase === 'DEPLOY') {
    if (initialDeployment.value && bothInitialSitesReady.value) showInitiativeFx()
    else initialDeployment.value ? completeInitialDeployment() : enterContest()
  }
  if (e.key.toLowerCase() === 'e' && game.value?.phase === 'CONTEST') endTurn()
  if (e.key === 'Escape') {
    deactivateBoardInteraction()
    clearSelection()
  }
}

function setupBattlefieldViewport() {
  if (!battlefieldViewport.value) return
  boardResizeObserver?.disconnect()
  boardResizeObserver = new ResizeObserver(() => resetBoardView())
  boardResizeObserver.observe(battlefieldViewport.value)
  resetBoardView()
}

function boardZoomLimits() {
  const viewport = battlefieldViewport.value
  if (!viewport) return { min: .3, max: 1.6 }
  const fit = Math.min(viewport.clientWidth / BOARD_WIDTH, viewport.clientHeight / BOARD_HEIGHT)
  return { min: Math.max(.22, fit * .72), max: 1.6 }
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function constrainBoardPan() {
  const viewport = battlefieldViewport.value
  if (!viewport) return
  const width = BOARD_WIDTH * boardZoom.value
  const height = BOARD_HEIGHT * boardZoom.value
  const margin = Math.min(88, Math.max(28, Math.min(viewport.clientWidth, viewport.clientHeight) * .14))
  boardPanX.value = width <= viewport.clientWidth
    ? (viewport.clientWidth - width) / 2
    : clamp(boardPanX.value, viewport.clientWidth - width - margin, margin)

  const centeredY = (viewport.clientHeight - height) / 2
  if (height <= viewport.clientHeight) {
    // Mobile's readable zoom can make the board a few pixels shorter than the viewport.
    // Keep a bounded vertical travel range instead of locking Y to the center.
    const verticalTravel = viewport.clientWidth < 720
      ? Math.min(120, Math.max(64, viewport.clientHeight * .18))
      : 0
    boardPanY.value = clamp(boardPanY.value, centeredY - verticalTravel, centeredY + verticalTravel)
  } else {
    boardPanY.value = clamp(boardPanY.value, viewport.clientHeight - height - margin, margin)
  }
}

function resetBoardView() {
  cancelBoardMomentum()
  const viewport = battlefieldViewport.value
  if (!viewport) return
  const fit = Math.min(viewport.clientWidth / BOARD_WIDTH, viewport.clientHeight / BOARD_HEIGHT)
  const emphasis = viewport.clientWidth < 720 ? 1.55 : 1.2
  // On narrow screens a pure fit-to-width view makes every realm too small to read.
  // Start closer and let the player pan; the minus button/pinch can still reveal the full map.
  const readableFloor = viewport.clientWidth < 480 ? .68 : 0
  const limits = boardZoomLimits()
  boardZoom.value = clamp(Math.max(fit * emphasis, readableFloor), limits.min, limits.max)
  boardPanX.value = (viewport.clientWidth - BOARD_WIDTH * boardZoom.value) / 2
  boardPanY.value = (viewport.clientHeight - BOARD_HEIGHT * boardZoom.value) / 2
  constrainBoardPan()
}

function setBoardZoom(nextZoom, focalX, focalY) {
  const viewport = battlefieldViewport.value
  if (!viewport) return
  const limits = boardZoomLimits()
  const zoom = clamp(nextZoom, limits.min, limits.max)
  if (Math.abs(zoom - boardZoom.value) < .001) return
  const focusX = focalX ?? viewport.clientWidth / 2
  const focusY = focalY ?? viewport.clientHeight / 2
  const worldX = (focusX - boardPanX.value) / boardZoom.value
  const worldY = (focusY - boardPanY.value) / boardZoom.value
  boardZoom.value = zoom
  boardPanX.value = focusX - worldX * zoom
  boardPanY.value = focusY - worldY * zoom
  constrainBoardPan()
}

function changeBoardZoom(delta) {
  setBoardZoom(boardZoom.value + delta)
}

function activateBoardInteraction() {
  if (boardInteractionActive.value) return
  boardInteractionActive.value = true
  nextTick(() => battlefieldViewport.value?.focus?.({ preventScroll: true }))
}

function deactivateBoardInteraction() {
  if (!boardInteractionActive.value) return
  boardInteractionActive.value = false
  activeBoardPointers.clear()
  boardDragStart = null
  boardPinchStart = null
  boardPanning.value = false
  cancelBoardMomentum()
}

function handleBoardInteractionOutside(event) {
  const viewport = battlefieldViewport.value
  if (!viewport || viewport.contains(event.target) || event.target?.closest?.('.battlefield-view-controls')) return
  deactivateBoardInteraction()
}

function zoomBattlefield(event) {
  if (!boardInteractionActive.value) return
  event.preventDefault()
  cancelBoardMomentum()
  const viewport = battlefieldViewport.value
  if (!viewport) return
  const rect = viewport.getBoundingClientRect()
  const factor = Math.exp(-event.deltaY * .0015)
  setBoardZoom(boardZoom.value * factor, event.clientX - rect.left, event.clientY - rect.top)
}

function pointerPosition(event) {
  const rect = battlefieldViewport.value?.getBoundingClientRect()
  return { x: event.clientX - (rect?.left || 0), y: event.clientY - (rect?.top || 0) }
}

function beginBoardPinch() {
  if (activeBoardPointers.size < 2) return
  const [a, b] = [...activeBoardPointers.values()]
  const centerX = (a.x + b.x) / 2
  const centerY = (a.y + b.y) / 2
  boardPinchStart = {
    distance: Math.hypot(b.x - a.x, b.y - a.y) || 1,
    zoom: boardZoom.value,
    worldX: (centerX - boardPanX.value) / boardZoom.value,
    worldY: (centerY - boardPanY.value) / boardZoom.value
  }
  for (const pointerId of activeBoardPointers.keys()) captureBoardPointer(pointerId)
  boardPanning.value = true
  blockBoardClick = true
}

function captureBoardPointer(pointerId) {
  const viewport = battlefieldViewport.value
  if (!viewport?.hasPointerCapture?.(pointerId)) viewport?.setPointerCapture?.(pointerId)
}

function cancelBoardMomentum() {
  cancelAnimationFrame(boardMomentumFrame)
  boardGliding.value = false
  boardSettling.value = false
  boardVelocityX = 0
  boardVelocityY = 0
  boardFeedbackX.value = 0
  boardFeedbackY.value = 0
}

function updateDirectionalFeedback(dx, dy, elapsed = 16) {
  const safeElapsed = Math.max(8, elapsed)
  boardVelocityX = clamp(dx / safeElapsed, -1.35, 1.35)
  boardVelocityY = clamp(dy / safeElapsed, -1.35, 1.35)
  boardFeedbackX.value = clamp(boardVelocityX / 1.1, -1, 1)
  boardFeedbackY.value = clamp(boardVelocityY / 1.1, -1, 1)
}

function startBoardMomentum() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || Math.hypot(boardVelocityX, boardVelocityY) < .08) {
    boardSettling.value = true
    clearTimeout(boardSettleTimer)
    boardSettleTimer = setTimeout(() => {
      boardSettling.value = false
      boardFeedbackX.value = 0
      boardFeedbackY.value = 0
    }, 160)
    return
  }
  boardGliding.value = true
  boardSettling.value = true
  let previousTime = performance.now()
  const glide = now => {
    const elapsed = Math.min(32, now - previousTime)
    previousTime = now
    const previousX = boardPanX.value
    const previousY = boardPanY.value
    boardPanX.value += boardVelocityX * elapsed
    boardPanY.value += boardVelocityY * elapsed
    constrainBoardPan()
    if (boardPanX.value === previousX) boardVelocityX = 0
    if (boardPanY.value === previousY) boardVelocityY = 0
    const decay = Math.pow(.86, elapsed / 16)
    boardVelocityX *= decay
    boardVelocityY *= decay
    boardFeedbackX.value = clamp(boardVelocityX / 1.1, -1, 1)
    boardFeedbackY.value = clamp(boardVelocityY / 1.1, -1, 1)
    if (Math.hypot(boardVelocityX, boardVelocityY) > .025) {
      boardMomentumFrame = requestAnimationFrame(glide)
    } else {
      boardGliding.value = false
      boardVelocityX = 0
      boardVelocityY = 0
      boardFeedbackX.value = 0
      boardFeedbackY.value = 0
      clearTimeout(boardSettleTimer)
      boardSettleTimer = setTimeout(() => { boardSettling.value = false }, 120)
    }
  }
  boardMomentumFrame = requestAnimationFrame(glide)
}

function startBoardPan(event) {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  if (!boardInteractionActive.value) {
    activateBoardInteraction()
    return
  }
  cancelBoardMomentum()
  if (event.target?.closest?.('button')) return
  const point = pointerPosition(event)
  activeBoardPointers.set(event.pointerId, point)
  clearTimeout(boardClickGuardTimer)
  clearTimeout(boardSettleTimer)
  if (activeBoardPointers.size === 1) {
    boardDragStart = { ...point, panX: boardPanX.value, panY: boardPanY.value }
    boardPinchStart = null
    boardDragFeedback = false
    boardLastMoveAt = performance.now()
  } else if (activeBoardPointers.size === 2) {
    beginBoardPinch()
  }
}

function moveBoardPan(event) {
  if (!activeBoardPointers.has(event.pointerId)) return
  const point = pointerPosition(event)
  activeBoardPointers.set(event.pointerId, point)
  if (activeBoardPointers.size >= 2) {
    event.preventDefault()
    if (!boardPinchStart) beginBoardPinch()
    const [a, b] = [...activeBoardPointers.values()]
    const centerX = (a.x + b.x) / 2
    const centerY = (a.y + b.y) / 2
    const distance = Math.hypot(b.x - a.x, b.y - a.y) || 1
    const limits = boardZoomLimits()
    const zoom = clamp(boardPinchStart.zoom * distance / boardPinchStart.distance, limits.min, limits.max)
    boardZoom.value = zoom
    boardPanX.value = centerX - boardPinchStart.worldX * zoom
    boardPanY.value = centerY - boardPinchStart.worldY * zoom
    constrainBoardPan()
    blockBoardClick = true
    boardVelocityX = 0
    boardVelocityY = 0
    boardFeedbackX.value = 0
    boardFeedbackY.value = 0
    return
  }
  if (!boardDragStart) return
  const dx = point.x - boardDragStart.x
  const dy = point.y - boardDragStart.y
  if (!boardPanning.value && Math.hypot(dx, dy) < 5) return
  event.preventDefault()
  captureBoardPointer(event.pointerId)
  boardPanning.value = true
  blockBoardClick = true
  if (!boardDragFeedback) { haptic('drag'); boardDragFeedback = true }
  const previousPanX = boardPanX.value
  const previousPanY = boardPanY.value
  boardPanX.value = boardDragStart.panX + dx
  boardPanY.value = boardDragStart.panY + dy
  constrainBoardPan()
  const now = performance.now()
  updateDirectionalFeedback(boardPanX.value - previousPanX, boardPanY.value - previousPanY, now - boardLastMoveAt)
  boardLastMoveAt = now
}

function endBoardPan(event) {
  activeBoardPointers.delete(event.pointerId)
  const viewport = battlefieldViewport.value
  if (viewport?.hasPointerCapture?.(event.pointerId)) viewport.releasePointerCapture(event.pointerId)
  if (activeBoardPointers.size === 1) {
    const point = [...activeBoardPointers.values()][0]
    boardDragStart = { ...point, panX: boardPanX.value, panY: boardPanY.value }
    boardPinchStart = null
    return
  }
  if (!activeBoardPointers.size) {
    const moved = boardPanning.value
    boardPanning.value = false
    if (moved) {
      haptic('release')
      startBoardMomentum()
    } else {
      boardFeedbackX.value = 0
      boardFeedbackY.value = 0
    }
    boardDragStart = null
    boardPinchStart = null
    clearTimeout(boardClickGuardTimer)
    boardClickGuardTimer = setTimeout(() => { blockBoardClick = false }, 0)
  }
}

function suppressBoardClick(event) {
  if (!blockBoardClick) return
  event.preventDefault()
  event.stopPropagation()
  blockBoardClick = false
}

function clearSelection() {
  selectedCard.value = null
  selectedUnit.value = null
  moveModeUnit.value = null
  cycleMode.value = false
}

function handCardStyle(card, index, total) {
  if (!total) return {}
  const center = (total - 1) / 2
  const offset = index - center
  const normalized = center ? offset / center : 0
  const distance = Math.abs(normalized)
  // Keep the middle card upright and lift the outside cards into a clear arc.
  const maxAngle = total >= 9 ? 25 : total >= 7 ? 29 : 23
  const maxLift = total >= 9 ? 38 : total >= 7 ? 44 : 34
  const overlap = total >= 10 ? -78 : total >= 8 ? -66 : total >= 6 ? -50 : total >= 4 ? -32 : -12
  return {
    '--fan-rotate': `${normalized * maxAngle}deg`,
    '--fan-lift': `${-Math.pow(distance, 1.55) * maxLift}px`,
    '--fan-rotate-mobile': `${normalized * maxAngle * 0.72}deg`,
    '--fan-lift-mobile': `${-Math.pow(distance, 1.55) * maxLift * 0.72}px`,
    '--fan-overlap': `${overlap}px`,
    zIndex: selectedCard.value?.id === card.id ? 220 : 100 + index
  }
}

function chooseCard(card) {
  if (!humanTurn.value || busy.value || drawReveal.value || phaseFx.value || combatFx.value || initiativeFx.value || (initialDeployment.value && bothInitialSitesReady.value)) return
  selectedUnit.value = null
  if (forcedDiscarding.value) {
    const toggledOff = selectedCard.value?.id === card.id
    selectedCard.value = toggledOff ? null : card
    tone('select')
    return
  }
  const toggledOff = selectedCard.value?.id === card.id
  selectedCard.value = toggledOff ? null : card
  tone('select')
  if (toggledOff) return
  if (card.cost > player.value.energy) return notify('灵力不足，但仍可弃置这张牌')
  if (game.value.phase !== 'DEPLOY' && card.type !== 'SPELL') return notify('争夺阶段只能使用瞬发术式')
  if (card.type === 'SPELL' && !['SEAL', 'SURGE', 'REINFORCE', 'RANGE', 'REFRESH'].includes(card.effectCode)) return play(card, {})
  if (card.type === 'SECRET') return play(card, {})
}

function sourceSite(unitId) {
  return game.value?.sites.find(site => site.units.some(unit => unit.instanceId === unitId))
}

function ringDistance(from, to) {
  const total = game.value?.sites.length || 0
  if (!total || from === to) return 0
  const center = total - 1
  if (from === center || to === center) return 1
  const ringSize = total - 1
  const delta = Math.abs(from - to)
  return Math.min(delta, ringSize - delta)
}

function distanceForSite(site) {
  if (!selectedUnit.value) return null
  const source = sourceSite(selectedUnit.value.instanceId)
  return source ? ringDistance(source.index, site.index) : null
}

function canAttackSite(site) {
  if (!selectedUnit.value || !site.ownerId || site.ownerId === localPlayerId.value) return false
  const distance = distanceForSite(site)
  return distance !== null && distance <= (selectedUnit.value.attackRange || 1)
}

function isSiteTargetable(site) {
  if (moveModeUnit.value) {
    if (game.value.phase !== 'DEPLOY' || site.ownerId !== localPlayerId.value) return false
    const from = sourceSite(moveModeUnit.value.instanceId)
    if (!from || from.index === site.index) return false
    return distanceBetween(from, site) === 1 && site.units.length < 2
  }
  if (selectedUnit.value) return game.value.phase === 'CONTEST' && canAttackSite(site)
  if (selectedCard.value?.type === 'SITE') {
    if (game.value.finalRound) return false
    return game.value.phase === 'DEPLOY' && (!site.ownerId || site.ownerId === localPlayerId.value)
  }
  if (selectedCard.value?.type === 'UNIT') return game.value.phase === 'DEPLOY' && site.ownerId === localPlayerId.value && site.units.length < 2
  return false
}

function distanceBetween(a, b) {
  if (!a || !b) return 99
  if (a.core || b.core) return a.index === b.index ? 0 : 1
  return Math.abs((a.row ?? 0) - (b.row ?? 0)) + Math.abs((a.column ?? 0) - (b.column ?? 0))
}

function rangeLineSites() {
  if (!selectedUnit.value) return []
  const source = sourceSite(selectedUnit.value.instanceId)
  if (!source) return []
  return (game.value?.sites || []).filter(site => canAttackSite(site)).map(site => site.index)
}

function isSiteOutOfRange(site) {
  return Boolean(selectedUnit.value && site.ownerId && site.ownerId !== localPlayerId.value && !canAttackSite(site))
}

function isSourceSite(site) {
  return Boolean(selectedUnit.value && sourceSite(selectedUnit.value.instanceId)?.index === site.index)
}

async function selectSite(site) {
  if (forcedDiscarding.value) return
  if (!humanTurn.value || busy.value || drawReveal.value || phaseFx.value || combatFx.value || initiativeFx.value || (initialDeployment.value && bothInitialSitesReady.value)) return
  if (moveModeUnit.value) {
    if (!isSiteTargetable(site)) return notify('只能调防到相邻且有空位的己方场地')
    await act(() => api.moveUnit(game.value.id, localPlayerId.value, moveModeUnit.value.instanceId, site.index), `调防至「${site.name}」`)
    moveModeUnit.value = null
    return
  }
  if (selectedCard.value && targetMode.value === 'site') {
    const freeSpell = selectedCard.value.type === 'SPELL' && selectedCard.value.cost === 1 && (player.value.momentum || 0) >= 3
    if (selectedCard.value.cost > player.value.energy && !freeSpell) return notify('灵力不足：需要更多灵力，或弃置该牌')
    if (!isSiteTargetable(site)) {
      if (selectedCard.value.type === 'UNIT') return notify(site.ownerId !== localPlayerId.value ? '单位只能部署到己方场地' : '该场地已驻扎2个单位，可先撤离或调防')
      if (game.value.finalRound) return notify('终局回合不可新部署场地')
      return notify(site.ownerId && site.ownerId !== localPlayerId.value ? '不能直接覆盖敌方场地，请先争夺' : '该场地不可作为目标')
    }
    return play(selectedCard.value, { targetSiteIndex: site.index })
  }
  if (selectedUnit.value && game.value.phase === 'CONTEST') {
    if (!site.ownerId) return notify('无主场地需要用场地卡部署')
    if (site.ownerId === localPlayerId.value) return notify('请选择敌方场地')
    if (selectedUnit.value.shaken) return notify('该单位处于动摇，本回合不可主动进攻')
    if (!canAttackSite(site)) {
      const distance = distanceForSite(site)
      return notify(`超出射程：目标距离 ${distance}，单位射程 ${selectedUnit.value.attackRange || 1}`)
    }
    await resolveAttack(site)
  }
}

function selectUnit(unit) {
  if (forcedDiscarding.value) return
  if (!humanTurn.value || busy.value || drawReveal.value || phaseFx.value || combatFx.value) return
  if (selectedCard.value && targetMode.value === 'unit') {
    const freeSpell = selectedCard.value.cost === 1 && (player.value.momentum || 0) >= 3
    if (selectedCard.value.cost > player.value.energy && !freeSpell) return notify('灵力不足')
    if (unit.ownerId !== targetUnitOwner.value) return notify(targetUnitOwner.value === localPlayerId.value ? '请选择己方单位' : '请选择敌方单位')
    return play(selectedCard.value, { targetUnitId: unit.instanceId })
  }
  if (game.value.phase === 'DEPLOY' && unit.ownerId === localPlayerId.value) {
    // 双击思路：再点同一单位进入调防
    if (moveModeUnit.value?.instanceId === unit.instanceId) {
      moveModeUnit.value = null
      return
    }
    moveModeUnit.value = unit
    selectedUnit.value = null
    selectedCard.value = null
    tone('select')
    return notify('已选调防单位：点击相邻己方场地（1灵力），Esc 取消')
  }
  if (game.value.phase !== 'CONTEST') return notify('部署阶段：点己方单位可调防；右上角可撤离')
  if (unit.ownerId !== localPlayerId.value) return notify('只能选择己方单位发起争夺')
  if (unit.exhausted || unit.sealed || unit.shaken) {
    return notify(unit.sealed ? '该单位正被封印' : unit.shaken ? '该单位动摇中，不可进攻' : '该单位本回合已行动')
  }
  selectedUnit.value = selectedUnit.value?.instanceId === unit.instanceId ? null : unit
  selectedCard.value = null
  moveModeUnit.value = null
  tone('select')
}

async function play(card, target) {
  if (forcedDiscarding.value) return
  if (card.cost > player.value.energy) return notify('灵力不足')
  const targetSiteIndex = target.targetSiteIndex
  await act(() => api.playCard(game.value.id, { playerId: localPlayerId.value, cardId: card.id, ...target }), `发动「${card.name}」`)
  selectedCard.value = null
  if (targetSiteIndex !== undefined) showPlacementFx(card, targetSiteIndex)
}

async function resolveAttack(site) {
  const attacker = selectedUnit.value
  const source = game.value.sites.find(item => item.units.some(unit => unit.instanceId === attacker.instanceId))
  const attackPower = attacker.power + (source?.effectCode === 'FORGE' ? 1 : 0) + (attacker.keyword === 'DUELIST' && site.units.length === 0 ? 1 : 0)
  const defense = site.baseGuard + site.units.filter(unit => !unit.sealed).reduce((sum, unit) => sum + unit.guard, 0)
  const targetOwnerBefore = site.ownerId
  combatFx.value = { attacker: { ...attacker, siteName: source?.name }, target: { ...site }, attackPower, defense, stage: 'charge', result: '', opponent: false, captured: false }
  busy.value = true
  clearSelection()
  await sleep(460)
  if (!combatFx.value) return
  combatFx.value.stage = 'clash'
  tone('impact')
  await sleep(260)
  try {
    const next = await api.attack(game.value.id, { playerId: localPlayerId.value, attackerUnitId: attacker.instanceId, targetSiteIndex: site.index })
    syncGame(next)
    combatFx.value.result = next.statusText
    combatFx.value.captured = next.sites?.find(item => item.index === site.index)?.ownerId !== targetOwnerBefore
    combatFx.value.stage = 'result'
    pulse.value = '争夺结算'
    clearTimeout(pulseTimer)
    pulseTimer = setTimeout(() => { pulse.value = '' }, 1000)
    await sleep(1500)
  } catch (e) {
    notify(e.message)
  } finally {
    combatFx.value = null
    busy.value = false
  }
}

async function completeInitialDeployment() {
  if (!humanTurn.value || !initialDeployment.value || game.value.phase !== 'DEPLOY' || busy.value) return
  if (!playerReady.value) return notify('\u8bf7\u5148\u90e8\u7f72\u81f3\u5c11\u4e00\u5f20\u5df1\u65b9\u573a\u5730\u5361')
  clearSelection()
  await act(() => api.endTurn(game.value.id, localPlayerId.value), '\u53cc\u65b9\u521d\u59cb\u573a\u5730\u5df2\u5c31\u4f4d')
}

async function enterContest() {
  if (!humanTurn.value || game.value.phase !== 'DEPLOY' || busy.value) return
  busy.value = true
  phaseFx.value = { title: '争夺阶段', subtitle: '部署完成 · 现在让你的单位发起冲锋', kind: 'contest' }
  tone('phase')
  await sleep(300)
  try {
    syncGame(await api.contest(game.value.id, localPlayerId.value))
    await sleep(1200)
  } catch (e) {
    notify(e.message)
  } finally {
    phaseFx.value = null
    busy.value = false
    clearSelection()
  }
}

async function endTurn() {
  if (!humanTurn.value || busy.value) return
  await finishEndTurn()
}

async function finishEndTurn() {
  if (!humanTurn.value || busy.value) return
  clearSelection()
  await act(() => api.endTurn(game.value.id, localPlayerId.value), '\u56de\u5408\u4ea4\u66ff')
}

async function retreatUnit(unit) {
  if (!humanTurn.value || game.value.phase !== 'DEPLOY' || busy.value) return
  await act(() => api.retreatUnit(game.value.id, localPlayerId.value, unit.instanceId), `撤离「${unit.name}」`)
  clearSelection()
}

async function discard(card) {
  if (!humanTurn.value || busy.value) return
  if (cycleMode.value) {
    await act(() => api.cycleCard(game.value.id, localPlayerId.value, card.id), `筛牌「${card.name}」`)
    cycleMode.value = false
    clearSelection()
    return
  }
  const allowed = game.value.phase === 'DEPLOY' || (game.value.phase === 'CONTEST' && forcedDiscarding.value)
  if (!allowed) return
  await act(() => api.discard(game.value.id, localPlayerId.value, card.id), `弃置「${card.name}」`)
  clearSelection()
  if (forcedDiscarding.value && handOverflow.value === 0) {
    forcedDiscarding.value = false
    await finishEndTurn()
  }
}

function toggleCycleMode() {
  if (!humanTurn.value || player.value?.cycleUsedThisTurn) return notify(player.value?.cycleUsedThisTurn ? '本回合已筛牌' : '无法筛牌')
  cycleMode.value = !cycleMode.value
  selectedCard.value = null
  selectedUnit.value = null
  moveModeUnit.value = null
  if (cycleMode.value) tone('select')
}

async function discardSelected() {
  if (selectedCard.value) await discard(selectedCard.value)
}

async function act(fn, label) {
  busy.value = true
  error.value = ''
  try {
    syncGame(await fn())
    pulse.value = label
    clearTimeout(pulseTimer)
    pulseTimer = setTimeout(() => { pulse.value = '' }, 900)
    tone('success')
  } catch (e) {
    notify(e.message)
  } finally {
    busy.value = false
  }
}

function showInitiativeFx() {
  const rollKey = `${game.value?.playerRoll || 0}:${game.value?.opponentRoll || 0}`
  if (!game.value?.playerRoll || !game.value?.opponentRoll || (initiativeFx.value && initiativeKey === rollKey)) return
  initiativeKey = rollKey
  clearInterval(initiativeRollTimer)
  clearTimeout(initiativeRevealTimer)
  initiativeFx.value = true
  initiativeStage.value = 'rolling'
  initiativePlayerRoll.value = 1 + Math.floor(Math.random() * 6)
  initiativeOpponentRoll.value = 1 + Math.floor(Math.random() * 6)
  tone('draw')
  initiativeRollTimer = setInterval(() => {
    initiativePlayerRoll.value = 1 + Math.floor(Math.random() * 6)
    initiativeOpponentRoll.value = 1 + Math.floor(Math.random() * 6)
  }, 80)
  initiativeRevealTimer = setTimeout(() => {
    clearInterval(initiativeRollTimer)
    initiativePlayerRoll.value = game.value.playerRoll
    initiativeOpponentRoll.value = game.value.opponentRoll
    initiativeStage.value = 'result'
    tone('success')
  }, 620)
}

function dismissInitiativeFx() {
  if (initiativeStage.value !== 'result') return
  clearInterval(initiativeRollTimer)
  clearTimeout(initiativeRevealTimer)
  initiativeFx.value = false
  initiativeStage.value = 'ready'
}

function queueDrawFx(newCards) {
  pendingDrawCards.push(...newCards)
  showPendingDrawFx()
}

function showPendingDrawFx() {
  if (!pendingDrawCards.length || opponentFxProcessing || opponentFxQueue.length || opponentFxBusy.value || initiativeFx.value || phaseFx.value || combatFx.value || drawReveal.value) return
  const cardsToReveal = pendingDrawCards.splice(0)
  showDrawFx(cardsToReveal)
}

function showDrawFx(newCards) {
  clearTimeout(revealTimer)
  clearTimeout(dismissRevealTimer)
  drawReveal.value = { cards: newCards, revealed: false }
  revealTimer = setTimeout(() => {
    if (drawReveal.value) drawReveal.value.revealed = true
  }, 420)
  dismissRevealTimer = setTimeout(() => dismissDrawFx(), 4200)
  tone('draw')
}

function dismissDrawFx() {
  clearTimeout(revealTimer)
  clearTimeout(dismissRevealTimer)
  drawReveal.value = null
  queueMicrotask(showPendingDrawFx)
}

function showPhaseFx(kind) {
  if (kind !== 'CONTEST') return
  clearTimeout(phaseTimer)
  phaseFx.value = { title: '争夺阶段', subtitle: '选中单位 → 指向敌方场地 → 观看战斗结算', kind }
  phaseTimer = setTimeout(() => { phaseFx.value = null }, 1450)
}

function showPlacementFx(card, targetSiteIndex) {
  clearTimeout(placementTimer)
  placementFx.value = { card, targetSiteIndex }
  tone('place')
  placementTimer = setTimeout(() => { placementFx.value = null }, 900)
}

function flashSite(index) {
  sitePulseIndex.value = index
  setTimeout(() => { if (sitePulseIndex.value === index) sitePulseIndex.value = null }, 900)
}

function enqueueOpponentFx(action) {
  if (!action || !rememberOpponentAction(action.key)) return
  opponentFxQueue.push(action)
  void processOpponentFxQueue()
}

async function waitForOpponentFxStage(runId) {
  while (runId === opponentFxRunId && (initiativeFx.value || phaseFx.value || drawReveal.value || (combatFx.value && !combatFx.value.opponent))) {
    await sleep(120)
  }
  return runId === opponentFxRunId
}

async function processOpponentFxQueue() {
  if (opponentFxProcessing) return
  opponentFxProcessing = true
  opponentFxBusy.value = true
  const runId = opponentFxRunId
  try {
    while (opponentFxQueue.length && runId === opponentFxRunId) {
      if (!await waitForOpponentFxStage(runId)) break
      const action = opponentFxQueue.shift()
      if (action.kind === 'attack') await playOpponentAttackFx(action, runId)
      else await playOpponentActionFx(action, runId)
    }
  } finally {
    if (runId === opponentFxRunId) {
      aiFx.value = null
      if (combatFx.value?.opponent) combatFx.value = null
      opponentFxProcessing = false
      opponentFxBusy.value = false
      showPendingDrawFx()
    }
  }
}

async function playOpponentActionFx(action, runId) {
  aiFx.value = { title: action.title || '\u5bf9\u624b\u884c\u52a8', detail: action.detail, kind: action.kind === 'target' ? 'attack' : action.kind }
  tone(action.kind === 'target' ? 'warning' : action.kind === 'thinking' ? 'select' : 'place')
  await sleep(action.kind === 'target' ? 900 : 780)
  if (runId === opponentFxRunId) aiFx.value = null
  await sleep(120)
}

async function playOpponentAttackFx(action, runId) {
  aiFx.value = { title: action.title || '\u4e89\u593a\u573a\u5730', detail: action.detail, kind: 'attack' }
  tone('warning')
  await sleep(720)
  if (runId !== opponentFxRunId) return
  aiFx.value = null
  combatFx.value = {
    attacker: action.attacker,
    target: action.target,
    attackPower: action.attackPower,
    defense: action.defense,
    stage: 'charge',
    result: action.result,
    opponent: true,
    captured: action.captured
  }
  await sleep(620)
  if (runId !== opponentFxRunId || !combatFx.value) return
  combatFx.value.stage = 'clash'
  tone('impact')
  await sleep(360)
  if (runId !== opponentFxRunId || !combatFx.value) return
  combatFx.value.stage = 'result'
  if (action.target?.index !== undefined) flashSite(action.target.index)
  tone(action.captured ? 'phase' : 'success')
  await sleep(1750)
  if (runId === opponentFxRunId && combatFx.value?.opponent) combatFx.value = null
  await sleep(180)
}

function notify(message) {
  error.value = message
  setTimeout(() => { if (error.value === message) error.value = '' }, 2600)
}

function haptic(kind = 'select') {
  if (!soundOn.value || !navigator.vibrate) return
  const patterns = {
    select: 8, success: 16, draw: [10, 20, 10], place: 18,
    impact: [35, 24, 45], phase: [16, 28, 22], tick: 12,
    warning: [24, 32, 24], victory: [18, 28, 32, 40, 50],
    defeat: [50, 35, 70], drag: 7, release: 10
  }
  navigator.vibrate(patterns[kind] || 10)
}

function tone(kind = 'success') {
  if (!soundOn.value) return
  haptic(kind)
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) return
    audioContext ||= new AudioContextClass()
    if (audioContext.state === 'suspended') audioContext.resume()
    const oscillator = audioContext.createOscillator()
    const gain = audioContext.createGain()
    oscillator.connect(gain)
    gain.connect(audioContext.destination)
    const frequencies = { select: 330, success: 420, draw: 260, place: 520, impact: 110, phase: 190, tick: 760, warning: 180, victory: 620, defeat: 95 }
    oscillator.type = ['impact', 'defeat', 'warning'].includes(kind) ? 'triangle' : 'sine'
    oscillator.frequency.value = frequencies[kind] || 420
    const duration = ['impact', 'victory', 'defeat'].includes(kind) ? .24 : .12
    gain.gain.setValueAtTime(['impact', 'defeat'].includes(kind) ? .06 : .035, audioContext.currentTime)
    gain.gain.exponentialRampToValueAtTime(.001, audioContext.currentTime + duration)
    oscillator.start()
    oscillator.stop(audioContext.currentTime + duration)
  } catch {}
}

function warnBeforeUnload(event) {
  if (!activeMatch.value) return
  event.preventDefault()
  event.returnValue = ''
}

function requestExit(target = '/') {
  if (!activeMatch.value) {
    allowRouteLeave.value = true
    router.push(target)
    return
  }
  pendingExitTarget.value = target
  exitError.value = ''
  showExitConfirm.value = true
  tone('warning')
}

function cancelExit() {
  showExitConfirm.value = false
  pendingExitTarget.value = '/'
  exitError.value = ''
}

async function confirmExit() {
  if (!game.value || exiting.value) return
  exiting.value = true
  error.value = ''
  exitError.value = ''
  try {
    const next = await api.leaveMatch(game.value.id, localPlayerId.value)
    syncGame(next)
    if (exitPenaltyApplies.value) localStorage.setItem('fieldrealm-match-ban-until', String(Date.now() + 30_000))
    allowRouteLeave.value = true
    showExitConfirm.value = false
    await router.push(pendingExitTarget.value || '/')
  } catch (e) {
    exitError.value = e.message
    notify(e.message)
  } finally {
    exiting.value = false
  }
}

async function rematch() {
  if (game.value?.ranked) return router.push('/')
  busy.value = true
  try {
    const next = await api.createMatch()
    router.replace(`/battle/${next.id}`)
    location.reload()
  } finally {
    busy.value = false
  }
}

function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)) }
const controlled = id => game.value?.sites.filter(site => site.ownerId === id).length || 0
/** 详细教程课程：先讲解，再穿插实操关卡 */
const tutorialCurriculum = [
  {
    id: 'welcome',
    chapter: '序章',
    title: '欢迎来到场地弈境',
    body: '我是雾隐教习。这不是互相砍血的卡牌——这里没有生命值，也没有击杀单位。你要争夺的是「场地归属」与「积分主动权」。',
    bullets: [
      '胜利方式一：控制过半场地，并连续两个结算阶段都保持，触发「场地绝杀」',
      '胜利方式二：打到规定回合结束，比谁的累计积分更高',
      '本教程约 8～10 分钟：我会先讲清界面，再带你亲手走完一局核心循环'
    ],
    tip: '建议：先读完讲解，再点「继续」；实操时我会暂时收起对话框。',
    practice: false
  },
  {
    id: 'board',
    chapter: '界面',
    title: '棋盘：九域与天元核心',
    body: '默认 3×3 共九格。中央是「天元核心」，积分权重 ×2，是兵家必争。四角多为「边陲」：部署更便宜，但该格积分计为 0。',
    bullets: [
      '绿边/己：你控制的场地',
      '红边/敌：对手控制的场地',
      '无主：需要用地场卡部署占领（不能直接攻击占领）',
      '相邻己方场地会触发「邻接协同」：守力与结算分更有优势'
    ],
    tip: '把棋盘想成连在一起的地盘，而不是九个独立格子。',
    practice: false
  },
  {
    id: 'hud',
    chapter: '界面',
    title: '读懂顶部与双方信息条',
    body: '顶部显示回合进度与当前阶段倒计时。上方是对手，下方是你。积分、控制格数、绝杀进度、气势都在这里。',
    bullets: [
      '积分：结算时由控制的场地提供，核心 ×2',
      '控制 x/9：你占了几块场地（绝杀需要过半，3×3 为 5 块）',
      '绝杀 0/2：连续结算达标次数；掉到不足过半会重置',
      '金色菱形气势：争夺成败会增减，满 3 可免费打 1 费术式',
      '✦ 灵力点：本回合可花的资源，不跨回合保留'
    ],
    tip: '人机教程时限较宽，不用慌，先看懂再动手。',
    practice: false
  },
  {
    id: 'cards',
    chapter: '卡牌',
    title: '四种卡牌各司其职',
    body: '底部手牌是你的行动库。点选卡牌后，棋盘上可点的目标会高亮。',
    bullets: [
      '◇ 场地卡：占领/改造格子，提供基础守力与积分',
      '✦ 单位卡：驻扎到己方场地，用战力进攻、用守力防守',
      '⌁ 术式：立即生效（抽牌、增幅、封印、加射程等）',
      '✺ 秘策：条件苛刻、威力大，每局限一次',
      '费用在左上角金圈；灵力不够会发灰，可弃牌或等下回合'
    ],
    tip: '桌面右键 / 手机长按卡牌可看详细效果。',
    practice: false
  },
  {
    id: 'turn_flow',
    chapter: '流程',
    title: '一个回合怎么走',
    body: '标准节奏是：抽牌与灵力刷新 → 部署阶段 → 争夺阶段 → 结算积分与绝杀进度 → 换手。',
    bullets: [
      '部署阶段：打牌、驻军、调防（1 灵力挪单位）、筛牌（弃1抽1）',
      '争夺阶段：用未行动单位攻击射程内的敌方场地',
      '战力 > 守力：夺取归属；壁垒场地通常要连续成功两次',
      '终局回合不能新放场地，只能术式、调防与争夺'
    ],
    tip: '快捷键：C 进入争夺/完成初始部署，E 结束回合，Esc 取消选择。',
    practice: false
  },
  {
    id: 'practice_site',
    chapter: '实操 1/4',
    title: '现在：部署你的第一块场地',
    body: '请完成一次「选场地卡 → 点无主格」。手牌里有「苍翠庭院」（费用 1），点它，再点棋盘任意无主格。',
    bullets: [
      '正确顺序：先点手牌，再点场地（不要反过来）',
      '不要点已经有主的敌方格——场地卡不能直接覆盖敌人',
      '边陲格（四角）费用可能 -1，但积分按 0 算',
      '成功后，该格会变成你的颜色，并显示场地名称与守力'
    ],
    tip: '操作指引：点下方手牌「苍翠庭院」→ 点棋盘空地。',
    practice: true,
    waitFor: 'deploy_unit',
    highlight: 'hand-site'
  },
  {
    id: 'practice_unit',
    chapter: '实操 2/4',
    title: '现在：驻扎一名单位',
    body: '场地站稳后，把单位放上去。点选手牌「星幕哨卫」（单位），再点你刚刚占领的己方场地。',
    bullets: [
      '单位只能放在己方场地上',
      '每个场地最多 2 个单位',
      '战力用于进攻，守力加到场地总守力',
      '单位不会被消灭；但所在场被夺时会「动摇」'
    ],
    tip: '若点错卡：再点一次取消，或按 Esc。',
    practice: true,
    waitFor: 'ready_contest',
    highlight: 'hand-unit'
  },
  {
    id: 'practice_ready',
    chapter: '实操 3/4',
    title: '现在：完成初始部署',
    body: '初始阶段双方都要先落下至少一块场地。你已完成，请点击右侧「完成部署」（或按 C），让我也布置场地，然后摇先手骰。',
    bullets: [
      '完成部署后，轮到教习（AI）自动落子',
      '双方都有场地后会摇骰决定谁先争夺',
      '先手骰只决定顺序，不直接决定胜负',
      '之后才会进入真正的「争夺阶段」'
    ],
    tip: '看右下/右侧绿色按钮：「完成部署」。',
    practice: true,
    waitFor: 'contest',
    highlight: 'phase-button'
  },
  {
    id: 'contest_rules',
    chapter: '争夺',
    title: '争夺怎么算赢',
    body: '争夺阶段：先点己方单位，再点金色高亮的敌方场地。只有「战力 > 总守力」才会易主。',
    bullets: [
      '总守力 = 场地基础守力 + 驻军守力 + 邻接等加成',
      '射程不够的目标会变暗，并提示「超出射程」',
      '夹击：目标被你 ≥2 块相邻场地夹住时，战力 +1',
      '压倒性（战力高出 3 点及以上）额外 +1 分',
      '单位本回合行动后会显示「已行动」，不能再攻'
    ],
    tip: '金色虚线/高亮 = 你当前可以打的合法目标。',
    practice: false
  },
  {
    id: 'practice_attack',
    chapter: '实操 4/4',
    title: '现在：发起一次争夺',
    body: '轮到你争夺时：点你的单位，再点敌方被高亮的场地。尽量选择守力较低、或你够得着的目标。',
    bullets: [
      '若提示动摇/已行动/封印：换一个单位',
      '若提示超出射程：换更近的目标，或下回用加射程术式',
      '成功后场地颜色会变成你的，绝杀与积分局面都会变化',
      '失败也不要紧：你会理解守力与气势的反馈'
    ],
    tip: '点单位 → 点金色敌方场地。完成后我会为你总结。',
    practice: true,
    waitFor: 'done',
    highlight: 'board'
  },
  {
    id: 'win_more',
    chapter: '进阶',
    title: '绝杀、积分与中盘技巧',
    body: '打完第一轮争夺后，你已经会核心操作。真正的对局还要管理这两条胜利线。',
    bullets: [
      '绝杀：控制 ≥5 格（3×3）并连续两个结算；HUD 上「绝杀 1/2」很危险',
      '被打断：控制掉到不足过半，进度清零',
      '积分运营：核心、邻接协同、场地常驻分、压倒性争夺',
      '调防：部署阶段花 1 灵力把单位挪到相邻己方场补洞',
      '筛牌：每回合可弃 1 抽 1；弃场地/单位还有额外小收益'
    ],
    tip: '标准局还有困难 AI、主题卡组，建议教程后再试「秘境试炼」。',
    practice: false
  },
  {
    id: 'status_guide',
    chapter: '进阶',
    title: '单位状态怎么看',
    body: '单位条右侧是战/守/射，名字旁的小色块是状态。悬停可看说明。',
    bullets: [
      '封：被封印，无法行动',
      '摇：动摇，守力降低且本回合不能进攻',
      '动：本回合已争夺',
      '新：刚部署的新驻标记',
      '根：扎根，连驻后守力 +1',
      '战+/射+：被术式等增幅过'
    ],
    tip: '状态是读局关键，比卡面光效更重要。',
    practice: false
  },
  {
    id: 'done',
    chapter: '结业',
    title: '教程完成，去掌控主动权',
    body: '你已经学会：部署场地、驻扎单位、完成布阵、按射程争夺，并理解了双胜利与状态。可以继续打完本局，或回大厅开标准对战。',
    bullets: [
      '忘记规则时：点右上角「规则」，或随时点「打开教程」回顾',
      '推荐下一局：3×3 + 标准难度 + 均衡卡组',
      '想练翻盘：试试「壁垒龟缩」或「游猎射程」主题组',
      '残局「核心突破」可专门练算射程与战力'
    ],
    tip: '雾海纪元见——愿你每一次落子，都改写归属。',
    practice: false
  }
]

const isTutorial = computed(() => game.value?.scenario === 'tutorial')
const tutorialCard = computed(() => {
  if (!isTutorial.value) return null
  const page = tutorialCurriculum[tutorialPageIndex.value] || tutorialCurriculum[0]
  return {
    ...page,
    step: tutorialPageIndex.value + 1,
    total: tutorialCurriculum.length
  }
})
const tutorialPracticeTip = computed(() => {
  if (!isTutorial.value || tutorialVisible.value) return ''
  const page = tutorialCurriculum[tutorialPageIndex.value]
  if (page?.practice && tutorialAwaiting.value) return page.tip || page.body
  return ''
})
const showTutorialCoach = computed(() => {
  if (!isTutorial.value || !tutorialCard.value) return false
  if (!tutorialVisible.value) return false
  if (tutorialAwaiting.value) return false
  return true
})

watch(() => game.value?.scenario, (s) => {
  if (s === 'tutorial') {
    tutorialPageIndex.value = 0
    tutorialVisible.value = true
    tutorialAwaiting.value = ''
    tutorialDismissedStep.value = ''
  }
}, { immediate: true })

watch(() => game.value?.tutorialStep, (step, prev) => {
  if (!isTutorial.value || !step || step === prev) return
  // 实操完成：后端步骤推进后，打开下一课
  if (tutorialAwaiting.value && step === tutorialAwaiting.value) {
    tutorialAwaiting.value = ''
    tutorialPageIndex.value = Math.min(tutorialPageIndex.value + 1, tutorialCurriculum.length - 1)
    tutorialVisible.value = true
    return
  }
  // 同步跳到与后端步骤对应的实操页（防止不同步）
  const map = { deploy_site: 'practice_site', deploy_unit: 'practice_unit', ready_contest: 'practice_ready', contest: 'practice_attack', done: 'done' }
  const id = map[step]
  if (id && !tutorialAwaiting.value) {
    const idx = tutorialCurriculum.findIndex(p => p.id === id)
    if (idx >= 0 && idx > tutorialPageIndex.value) {
      tutorialPageIndex.value = idx
      tutorialVisible.value = true
    }
  }
})

function dismissTutorial() {
  const page = tutorialCurriculum[tutorialPageIndex.value]
  if (page?.practice) {
    tutorialAwaiting.value = page.waitFor || ''
    tutorialVisible.value = false
    return
  }
  tutorialVisible.value = false
  tutorialDismissedStep.value = page?.id || ''
}

function advanceTutorialPage() {
  const page = tutorialCurriculum[tutorialPageIndex.value]
  if (page?.practice) {
    dismissTutorial()
    return
  }
  if (tutorialPageIndex.value >= tutorialCurriculum.length - 1) {
    tutorialVisible.value = false
    return
  }
  tutorialPageIndex.value += 1
  tutorialVisible.value = true
  // 若下一页是实操且后端已超过该关，自动跳过等待
  const next = tutorialCurriculum[tutorialPageIndex.value]
  if (next?.practice && game.value?.tutorialStep === next.waitFor) {
    tutorialPageIndex.value = Math.min(tutorialPageIndex.value + 1, tutorialCurriculum.length - 1)
  }
}

function reopenTutorial() {
  tutorialAwaiting.value = ''
  tutorialVisible.value = true
}

function siteSvgX(site) {
  if (!site) return 50
  const size = game.value?.boardSize || 3
  return ((site.column + 0.5) / size) * 100
}
function siteSvgY(site) {
  if (!site) return 50
  const size = game.value?.boardSize || 3
  return ((site.row + 0.5) / size) * 100
}
</script>

<template>
  <div class="battle-page" :class="{ 'is-busy': busy, 'contest-mode': game?.phase === 'CONTEST' }">
    <div v-if="loading" class="battle-loading"><LoaderCircle class="spin"/><b>正在展开九域棋盘</b><span>同步场地与卡组数据…</span></div>
    <div v-else-if="!game" class="battle-loading error-screen"><b>无法进入弈境</b><span>{{ error }}</span><button class="primary-button" @click="router.push('/')">返回大厅</button></div>
    <template v-else>
      <header class="battle-topbar">
        <button class="battle-brand" @click="requestExit('/')"><ArrowLeft :size="18"/><span class="brand-mark"><i></i><b></b></span><strong>场地弈境</strong></button>
        <div class="round-meter"><span>第 {{ Math.min(game.round, maxRounds) }} / {{ maxRounds }} 回合</span><div><i :style="{ width: `${roundProgress}%` }"></i></div><b :class="{ urgent: phaseClockUrgent }">{{ phaseLabel }} <em v-if="phaseClockLabel">{{ phaseClockLabel }}</em></b></div>
        <div class="battle-tools"><button @click="soundOn = !soundOn" :aria-label="soundOn ? '关闭声音与触感' : '开启声音与触感'"><Volume2 v-if="soundOn"/><VolumeX v-else/></button><button @click="showLog = !showLog"><History/><span>战局记录</span></button><button @click="requestExit('/rules')"><BookOpen/><span>规则</span></button><button aria-label="退出对局" @click="requestExit('/')"><LogOut/></button></div>
      </header>

      <section class="opponent-strip player-strip rival-strip">
        <div class="combatant"><span class="combatant-avatar rival-avatar">{{ rival.avatar }}</span><div><small>{{ rival.title }}</small><b>{{ rival.name }}</b></div></div>
        <div class="resource-cluster">
          <span class="dominion-meter" :class="{ hot: rivalKillProgress >= 1 }" title="绝杀进度">绝杀 <b>{{ rivalKillProgress }}</b>/2</span>
          <span class="control-count">控制 <b>{{ controlled(rivalPlayerId) }}</b>/{{ game.sites.length }}</span>
          <span class="momentum-pips" title="气势" aria-label="对手气势"><i v-for="n in 3" :key="'rm'+n" :class="{ filled: n <= (rival.momentum || 0) }">◆</i></span>
          <span class="score-orb rival-score"><small>积分</small><b>{{ rival.score }}</b></span>
          <span class="hand-count"><Hand/>{{ rival.hand.length }}</span>
        </div>
      </section>

      <main class="battlefield">
        <div class="field-mist mist-a"></div><div class="field-mist mist-b"></div>
        <div class="battle-lane-label">
          <span>战场态势</span>
          <b>{{ game.phase === 'CONTEST' ? '目标锁定 · 选择你的攻击路线' : initialDeployment ? '初始布阵 · 双方场地就位后开启争夺' : '部署窗口 · 调整你的阵地' }}</b>
          <em v-if="killThreat" class="kill-threat">{{ killThreat }}</em>
        </div>
        <div v-if="initialDeployment" class="deployment-readiness" aria-label="双方初始场地部署状态">
          <span :class="{ ready: playerReady }"><CheckCircle2/>你方{{ playerReady ? '已就位' : '未布置' }}</span>
          <i></i>
          <span :class="{ ready: rivalReady }"><CheckCircle2/>对手{{ rivalReady ? '已就位' : '待布阵' }}</span>
        </div>
        <div
          ref="battlefieldViewport"
          class="battlefield-viewport"
          :class="{ 'board-interaction-active': boardInteractionActive, 'is-panning': boardPanning, 'is-settling': boardSettling, 'is-gliding': boardGliding }"
          :style="boardFeedbackStyle"
          role="region"
          tabindex="0"
          :aria-label="boardInteractionActive ? '\u6218\u573a\u5df2\u805a\u7126\uff0c\u53ef\u62d6\u52a8\u6216\u7f29\u653e\u573a\u5730' : '\u6218\u573a\u672a\u805a\u7126\uff0c\u6ed1\u52a8\u5c06\u6eda\u52a8\u9875\u9762\uff1b\u70b9\u51fb\u540e\u53ef\u79fb\u52a8\u573a\u5730'"
          @focus="activateBoardInteraction"
          @wheel="zoomBattlefield"
          @pointerdown="startBoardPan"
          @pointermove="moveBoardPan"
          @pointerup="endBoardPan"
          @pointercancel="endBoardPan"
          @click.capture="suppressBoardClick"
        >
          <div class="battlefield-canvas" :style="{ transform: boardTransform }">
            <div class="sites-grid" :style="{ '--board-size': game.boardSize || 3 }">
              <RealmSite
                v-for="site in game.sites"
                :key="site.index"
                :style="{ gridRow: site.row + 1, gridColumn: site.column + 1 }"
                :site="site"
                :players="game.players"
                :local-player-id="localPlayerId"
                :active-player="active.id"
                :selected-unit-id="selectedUnit?.instanceId || moveModeUnit?.instanceId"
                :target-mode="targetMode"
                :targetable="isSiteTargetable(site)"
                :in-range-line="rangeLineSites().includes(site.index)"
                :target-unit-owner="targetUnitOwner"
                :distance="distanceForSite(site)"
                :out-of-range="isSiteOutOfRange(site)"
                :source="isSourceSite(site) || (moveModeUnit && sourceSite(moveModeUnit.instanceId)?.index === site.index)"
                :impacting="sitePulseIndex === site.index"
                :can-retreat="humanTurn && game.phase === 'DEPLOY'"
                @select-site="selectSite"
                @select-unit="selectUnit"
                @retreat-unit="retreatUnit"
              />
              <svg class="realm-connections" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                <polyline points="16.7,16.7 50,16.7 83.3,16.7 83.3,50 83.3,83.3 50,83.3 16.7,83.3 16.7,50 16.7,16.7" />
                <line x1="50" y1="50" x2="16.7" y2="16.7" /><line x1="50" y1="50" x2="50" y2="16.7" /><line x1="50" y1="50" x2="83.3" y2="16.7" />
                <line x1="50" y1="50" x2="83.3" y2="50" /><line x1="50" y1="50" x2="83.3" y2="83.3" /><line x1="50" y1="50" x2="50" y2="83.3" />
                <line x1="50" y1="50" x2="16.7" y2="83.3" /><line x1="50" y1="50" x2="16.7" y2="50" />
                <template v-if="selectedUnit && sourceSite(selectedUnit.instanceId)">
                  <line
                    v-for="idx in rangeLineSites()"
                    :key="'rl'+idx"
                    class="range-pulse-line"
                    :x1="siteSvgX(sourceSite(selectedUnit.instanceId))"
                    :y1="siteSvgY(sourceSite(selectedUnit.instanceId))"
                    :x2="siteSvgX(game.sites.find(s => s.index === idx))"
                    :y2="siteSvgY(game.sites.find(s => s.index === idx))"
                  />
                </template>
              </svg>
            </div>
          </div>
        </div>
        <div class="battlefield-view-controls" aria-label="战场视野控制">
          <span class="view-control-hint"><Move :size="13"/>{{ boardInteractionActive ? '\u573a\u5730\u5df2\u805a\u7126\uff1a\u62d6\u52a8 / \u6eda\u8f6e\u7f29\u653e' : '\u70b9\u51fb\u573a\u5730\u540e\u624d\u53ef\u79fb\u52a8' }}</span>
          <button type="button" title="缩小视野（-）" aria-label="缩小战场视野" @click="changeBoardZoom(-.12)"><Minus/></button>
          <output aria-live="polite">{{ boardZoomLabel }}</output>
          <button type="button" title="放大视野（+）" aria-label="放大战场视野" @click="changeBoardZoom(.12)"><Plus/></button>
          <button type="button" title="重置视野（0）" aria-label="重置战场视野" @click="resetBoardView"><LocateFixed/></button>
        </div>
        <div class="battle-flow-hint"><span class="hint-dot"></span><span v-if="game.phase === 'DEPLOY'">{{ initialDeployment ? '先部署己方场地，再完成部署等待对手场地显现' : '从下方手牌开始：选卡后，点击场地放下它' }}</span><span v-else>金色高亮为射程内目标；中央天元与八个外域均相邻</span></div>
        <div v-if="tutorialPracticeTip" class="tutorial-practice-bar" role="status">
          <img src="/teacher.png" alt="" class="tutorial-practice-avatar" />
          <div>
            <b>实操中 · {{ tutorialCard?.chapter }}</b>
            <p>{{ tutorialPracticeTip }}</p>
          </div>
          <button type="button" class="secondary-button" @click="reopenTutorial">重看说明</button>
        </div>
        <button v-if="isTutorial && !showTutorialCoach && !tutorialPracticeTip" type="button" class="tutorial-reopen" @click="reopenTutorial">打开教程</button>
        <transition name="impact"><div v-if="pulse" class="impact-banner"><Sparkles/>{{ pulse }}</div></transition>
        <transition name="fade"><div v-if="error" class="battle-toast"><X :size="16"/>{{ error }}</div></transition>
        <transition name="impact"><div v-if="aiFx" class="ai-action-banner" :class="`ai-${aiFx.kind}`"><span class="ai-action-icon"><Swords v-if="aiFx.kind === 'attack'"/><Sparkles v-else/></span><div><small>OPPONENT ACTION</small><b>{{ rival?.name || '\u5bf9\u624b' }} &#183; {{ aiFx.title || '\u5bf9\u624b\u884c\u52a8' }}</b><p>{{ aiFx.detail }}</p></div></div></transition>
        <div v-if="placementFx" class="placement-flight" :class="`to-site-${placementFx.targetSiteIndex}`" :style="{ '--flight-color': placementFx.card ? '#63d2a5' : '#efc36f' }"><span class="flight-trail"></span><GameCard v-if="placementFx.card" class="flight-card" :card="placementFx.card" compact disabled/><span class="flight-label">{{ placementFx.card.name }}</span><Zap :size="18"/></div>
      </main>

      <section class="player-strip self-strip">
        <div class="combatant"><span class="combatant-avatar">{{ player.avatar }}</span><div><small>{{ player.title }}</small><b>{{ player.name }}</b></div></div>
        <div class="turn-guidance"><span :class="{ active: humanTurn }">{{ humanTurn ? '你的回合' : '等待对手' }}</span><p>{{ instruction }}</p><div class="action-guide"><span v-for="(step, index) in actionSteps" :key="step.label" :class="{ done: step.done, current: index === (selectedCard || selectedUnit ? 1 : 0) }"><i>{{ step.done ? '✓' : index + 1 }}</i>{{ step.label }}</span></div></div>
        <div class="resource-cluster">
          <span class="energy-pips" title="本回合灵力"><i v-for="n in (game.boardSize || 3)" :key="n" :class="{ filled: n <= player.energy }">✦</i></span>
          <span class="momentum-pips" title="气势（满3可免费1费术式）" aria-label="气势"><i v-for="n in 3" :key="'m'+n" :class="{ filled: n <= (player.momentum || 0) }">◆</i></span>
          <span class="dominion-meter" :class="{ hot: playerKillProgress >= 1 }" title="绝杀进度">绝杀 <b>{{ playerKillProgress }}</b>/2</span>
          <span class="control-count">控制 <b>{{ controlled(localPlayerId) }}</b>/{{ game.sites.length }}</span>
          <span class="score-orb"><small>积分</small><b>{{ player.score }}</b></span>
        </div>
      </section>

      <section class="hand-area">
        <div v-if="forcedDiscarding" class="discard-warning forced-discard-warning">&#x56de;&#x5408;&#x7ed3;&#x7b97;&#x4e2d;&#xff1a;&#x8fd8;&#x9700;&#x5f03;&#x7f6e; {{ handOverflow }} &#x5f20;&#x624b;&#x724c;</div>
        <div v-else-if="handOverflow" class="hand-limit-note">&#x672c;&#x56de;&#x5408;&#x53ef;&#x7ee7;&#x7eed;&#x884c;&#x52a8;&#xff0c;&#x7ed3;&#x675f;&#x56de;&#x5408;&#x65f6;&#x9700;&#x5f03;&#x724c;</div>
        <div class="hand-caption"><span>你的手牌</span><b>{{ hand.length }} / 7</b><small>单击选择 · 长按详情 · 左右滑动翻牌</small></div>
        <TransitionGroup name="hand-card" tag="div" class="hand-cards">
          <GameCard
            v-for="(card, i) in hand"
            :key="`${card.id}-${i}`"
            :card="card"
            compact
            :selected="selectedCard?.id === card.id"
            :class="{ unaffordable: card.cost > player.energy }"
            :style="handCardStyle(card, i, hand.length)"
            :disabled="!humanTurn"
            @select="chooseCard"
          />
        </TransitionGroup>
        <div class="turn-actions">
          <button v-if="selectedCard && humanTurn && (game.phase === 'DEPLOY' || forcedDiscarding)" class="discard-selected" :disabled="busy" @click.stop="discardSelected"><Trash2/><span>&#x5f03;&#x7f6e;&#x9009;&#x4e2d;&#x724c;</span></button>
          <button
            v-if="humanTurn && !forcedDiscarding"
            class="discard-selected cycle-btn"
            type="button"
            :disabled="busy || player.cycleUsedThisTurn"
            :class="{ active: cycleMode }"
            @click="toggleCycleMode"
          >{{ cycleMode ? '取消筛牌' : '筛牌 弃1抽1' }}</button>
          <button v-if="game.phase === 'DEPLOY' && initialDeployment" class="phase-button ready-deployment" :disabled="!humanTurn || busy || forcedDiscarding || !playerReady" @click="completeInitialDeployment"><CheckCircle2/><span>完成部署</span><small>C</small></button>
          <button v-else-if="game.phase === 'DEPLOY'" class="phase-button" :disabled="!humanTurn || busy" @click="enterContest"><Swords/><span>进入争夺</span><small>C</small></button>
          <button v-else-if="game.phase === 'CONTEST'" class="phase-button end" :disabled="!humanTurn || busy || forcedDiscarding" @click="endTurn"><ChevronRight/><span>结束回合</span><small>E</small></button>
        </div>
      </section>

      <transition name="drawer"><aside v-if="showLog" class="battle-log"><header><div><span>战局记录</span><b>第 {{ game.round }} 回合</b></div><button @click="showLog = false"><X/></button></header><ol><li v-for="(line, i) in game.log" :key="i"><i></i><span>{{ line }}</span></li></ol></aside></transition>

      <transition name="fade"><div v-if="showExitConfirm" class="exit-confirm-overlay" role="dialog" aria-modal="true" aria-labelledby="exit-confirm-title"><div class="exit-confirm-card"><span class="exit-warning-icon"><LogOut/></span><small>LEAVE MATCH</small><h2 id="exit-confirm-title">确认退出当前对局？</h2><p v-if="exitPenaltyApplies">退出后对手将直接获胜，你将在 <strong>30 秒</strong> 内无法创建、加入或匹配新对局。</p><p v-else>退出后本局人机试炼将立即结束，<strong>不会触发 30 秒禁赛</strong>。</p><p v-if="exitError" class="exit-confirm-error" role="alert">{{ exitError }}</p><div><button class="secondary-button" :disabled="exiting" @click="cancelExit">继续对局</button><button class="danger-button" :disabled="exiting" @click="confirmExit">{{ exiting ? '正在退出…' : '确认退出' }}</button></div></div></div></transition>

      <transition name="draw-reveal">
        <div v-if="drawReveal" class="draw-overlay">
          <div class="draw-stage" :class="{ revealed: drawReveal.revealed }">
            <div class="draw-shadow"></div><div class="draw-kicker"><span>ROUND {{ game.round }}</span><b>灵脉回应了你的召唤</b></div>
            <div class="draw-deck"><span>✦</span><small>DRAW</small></div>
            <TransitionGroup name="draw-card" tag="div" class="draw-cards">
              <GameCard v-for="(card, index) in (drawReveal.revealed ? drawReveal.cards : [])" :key="`${card.id}-${index}`" :card="card"/>
            </TransitionGroup>
            <p>本回合获得 {{ drawReveal.cards.length }} 张卡牌</p><button class="secondary-button draw-accept" @click="dismissDrawFx">收下卡牌 <ChevronRight :size="15"/></button>
          </div>
        </div>
      </transition>

      <transition name="phase-overlay">
        <div v-if="initiativeFx" class="initiative-overlay">
          <div class="initiative-card" :class="`initiative-${initiativeStage}`" role="dialog" aria-modal="true" aria-live="polite">
            <div class="initiative-heading"><Dices/><span>先手骰</span><small>FIRST CONTEST INITIATIVE</small></div>
            <p class="initiative-lead">双方场地已就位，摇骰决定谁先发起第一次争夺</p>
            <div class="dice-duel">
              <div :class="{ winner: initiativeStage === 'result' && game.contestStarterIndex === 0 }">
                <small>{{ player.name }}</small><span class="dice-label">你方</span>
                <div class="die-shell" :class="{ rolling: initiativeStage === 'rolling' }"><b>{{ dieGlyph(initiativePlayerRoll) }}</b></div>
                <strong>{{ initiativeStage === 'rolling' ? '摇骰中' : initiativePlayerRoll + ' 点' }}</strong>
              </div>
              <i>VS</i>
              <div :class="{ winner: initiativeStage === 'result' && game.contestStarterIndex === 1 }">
                <small>{{ rival.name }}</small><span class="dice-label">对手</span>
                <div class="die-shell" :class="{ rolling: initiativeStage === 'rolling' }"><b>{{ dieGlyph(initiativeOpponentRoll) }}</b></div>
                <strong>{{ initiativeStage === 'rolling' ? '摇骰中' : initiativeOpponentRoll + ' 点' }}</strong>
              </div>
            </div>
            <p v-if="initiativeStage === 'result'" class="initiative-result"><strong>{{ contestStarter?.name }}</strong> 点数更高，获得第一次争夺先手。</p>
            <p v-else class="initiative-result rolling-copy">骰面正在翻转，先手即将揭晓…</p>
            <button class="secondary-button" :disabled="initiativeStage !== 'result'" @click="dismissInitiativeFx">进入战场 <ChevronRight/></button>
          </div>
        </div>
      </transition>

      <transition name="phase-overlay"><div v-if="phaseFx" class="phase-overlay"><div class="phase-crest"><span>✦</span><small>PHASE SHIFT</small></div><p>NOW ENTERING</p><h2>{{ phaseFx.title }}</h2><span>{{ phaseFx.subtitle }}</span><div class="phase-line"><i></i></div></div></transition>

      <transition name="combat-overlay">
        <div v-if="combatFx" class="combat-overlay" :class="[`combat-${combatFx.stage}`, { 'opponent-combat': combatFx.opponent, 'combat-captured': combatFx.captured }]">
          <div class="combat-topline">
            <span>{{ combatFx.opponent ? 'OPPONENT CONTEST' : 'CONTEST RESOLUTION' }}</span>
            <b>{{ combatFx.opponent ? `${rival?.name || '对手'}发起争夺` : '我方发起争夺' }}</b>
            <small v-if="combatFx.attacker.siteName" class="combat-route">「{{ combatFx.attacker.siteName }}」 → 「{{ combatFx.target.name }}」</small>
          </div>
          <div class="duel-scene">
            <div class="combat-side attacker-side"><span class="combat-avatar">{{ combatFx.attacker.name.slice(-1) }}</span><small>{{ combatFx.opponent ? '敌方进攻单位' : '进攻单位' }}</small><b>{{ combatFx.attacker.name }}</b><strong>{{ combatFx.attackPower }} <em>战力</em></strong></div>
            <div class="combat-middle"><span class="energy-ring"></span><Swords :size="40"/><i></i><b>{{ combatFx.stage === 'charge' ? (combatFx.opponent ? '敌方锁定目标' : '蓄势冲锋') : combatFx.stage === 'clash' ? '交锋！' : combatFx.captured ? '领域归属易手' : '争夺结算完成' }}</b></div>
            <div class="combat-side defender-side"><span class="combat-site-glyph">◇</span><small>被争夺领域</small><b>{{ combatFx.target.name }}</b><strong>{{ combatFx.defense }} <em>守力</em></strong></div>
          </div>
          <div v-if="combatFx.stage === 'result'" class="combat-result"><Zap :size="17"/><span>{{ combatFx.result }}</span><b>{{ combatFx.captured ? '领域已易手' : '领域仍守住' }}</b></div>
        </div>
      </transition>

      <transition name="fade">
        <div v-if="showTutorialCoach" class="tutorial-coach" role="dialog" aria-modal="true" aria-labelledby="tutorial-title">
          <div class="tutorial-coach-dim" @click="dismissTutorial"></div>
          <div class="tutorial-coach-panel">
            <div class="tutorial-coach-figure">
              <img class="coach-portrait" src="/teacher.png" alt="雾隐教习" width="280" height="360" decoding="async" />
              <small>雾隐教习</small>
            </div>
            <div class="tutorial-coach-dialog">
              <header>
                <span>{{ tutorialCard.chapter }} · {{ tutorialCard.step }}/{{ tutorialCard.total }}</span>
                <div class="tutorial-step-dots dense">
                  <i v-for="n in tutorialCard.total" :key="n" :class="{ on: n <= tutorialCard.step, current: n === tutorialCard.step }"></i>
                </div>
              </header>
              <h2 id="tutorial-title">{{ tutorialCard.title }}</h2>
              <p class="tutorial-body">{{ tutorialCard.body }}</p>
              <ul v-if="tutorialCard.bullets?.length" class="tutorial-bullets">
                <li v-for="(line, idx) in tutorialCard.bullets" :key="idx">{{ line }}</li>
              </ul>
              <p class="tutorial-tip">{{ tutorialCard.tip }}</p>
              <footer>
                <button v-if="tutorialCard.practice" type="button" class="secondary-button" @click="reopenTutorial">再看一遍</button>
                <button v-else type="button" class="secondary-button" @click="dismissTutorial">稍后再看</button>
                <button type="button" class="primary-button" @click="advanceTutorialPage">
                  {{ tutorialCard.id === 'done' ? '开始自由对弈' : tutorialCard.practice ? '我去操作' : '下一页' }}
                </button>
              </footer>
            </div>
          </div>
        </div>
      </transition>

      <div v-if="game.waitingForOpponent" class="victory-overlay waiting-overlay"><div class="victory-card"><span class="victory-sigil">&#9203;</span><small>ONLINE ROOM</small><h2>&#31561;&#24453;&#23545;&#25163;&#21152;&#20837;</h2><p>&#25226;&#25151;&#38388;&#21495;&#21457;&#32473;&#26379;&#21451;</p><div class="room-code">{{ game.id }}</div><button class="secondary-button" @click="router.push('/')">&#36820;&#22238;&#22823;&#21381;</button></div></div>
      <div v-if="game.phase === 'FINISHED'" class="victory-overlay"><div class="victory-card" :class="{ lost: game.winnerId !== localPlayerId }"><span class="victory-sigil">{{ game.winnerId === localPlayerId ? '✦' : '◇' }}</span><small>{{ game.victoryType }}</small><h2>{{ game.winnerId === localPlayerId ? '主动权尽在掌握' : '棋局仍有回响' }}</h2><p>{{ game.statusText }}</p><div class="final-score"><span><b>{{ player.score }}</b><small>{{ player.name }}</small></span><i>:</i><span><b>{{ rival.score }}</b><small>{{ rival.name }}</small></span></div><div v-if="game.ranked" class="rating-result" :class="{ positive: ratingDelta >= 0, negative: ratingDelta < 0 }"><small>本场天梯积分</small><strong>{{ ratingDirection }} {{ ratingDelta >= 0 ? `+${ratingDelta}` : ratingDelta }}</strong><span v-if="ratingAfter !== undefined">当前积分 {{ ratingAfter }}</span></div><div class="victory-actions"><button v-if="!game.ranked" class="primary-button" @click="rematch"><RotateCcw/>再弈一局</button><button class="secondary-button" @click="router.push('/')">返回大厅</button></div></div></div>
    </template>
  </div>
</template>
