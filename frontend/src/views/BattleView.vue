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
const initiativeFx = ref(false)
const initiativeStage = ref('ready')
const initiativePlayerRoll = ref(1)
const initiativeOpponentRoll = ref(1)
const battlefieldViewport = ref(null)
const boardZoom = ref(1)
const boardPanX = ref(0)
const boardPanY = ref(0)
const boardPanning = ref(false)
const boardSettling = ref(false)
const showExitConfirm = ref(false)
const pendingExitTarget = ref('/')
const allowRouteLeave = ref(false)
const exiting = ref(false)
const BOARD_WIDTH = 1320
const BOARD_HEIGHT = 820
const activeBoardPointers = new Map()
let boardDragStart = null
let boardPinchStart = null
let blockBoardClick = false
let boardClickGuardTimer
let boardSettleTimer
let boardDragFeedback = false
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
let aiTimer
let countdownTimer

const cardMap = computed(() => Object.fromEntries(cards.value.map(c => [c.id, c])))
const localPlayerId = computed(() => game.value?.players?.find(p => auth.user?.id && p.accountId === auth.user.id)?.id || localStorage.getItem(`fieldrealm-player-${game.value?.id}`) || 'p1')
const rivalPlayerId = computed(() => localPlayerId.value === 'p1' ? 'p2' : 'p1')
const localPlayerIndex = computed(() => game.value?.players?.findIndex(p => p.id === localPlayerId.value) ?? 0)

const player = computed(() => game.value?.players?.[localPlayerIndex.value])
const rival = computed(() => game.value?.players?.[1 - localPlayerIndex.value])
const active = computed(() => game.value?.players?.[game.value?.activePlayerIndex])
const humanTurn = computed(() => !game.value?.waitingForOpponent && active.value?.id === localPlayerId.value && game.value?.phase !== 'FINISHED')
const hand = computed(() => player.value?.hand?.map(id => cardMap.value[id]).filter(Boolean) || [])
const playerReady = computed(() => Boolean(game.value?.sites?.some(site => site.ownerId === localPlayerId.value)))
const rivalReady = computed(() => Boolean(game.value?.sites?.some(site => site.ownerId === rivalPlayerId.value)))
const initialDeployment = computed(() => !game.value?.initialContestResolved)
const bothInitialSitesReady = computed(() => Boolean(playerReady.value && rivalReady.value))
const dieGlyph = value => ['', '\u2680', '\u2681', '\u2682', '\u2683', '\u2684', '\u2685'][Number(value) || 1]
const contestStarter = computed(() => game.value?.players?.[game.value?.contestStarterIndex])
const boardTransform = computed(() => `translate3d(${boardPanX.value}px, ${boardPanY.value}px, 0) scale(${boardZoom.value})`)
const boardZoomLabel = computed(() => `${Math.round(boardZoom.value * 100)}%`)
const targetMode = computed(() => {
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
  if (game.value.waitingForOpponent) return `\u623f\u95f4\u53f7 ${game.value.id} \u00b7 \u7b49\u5f85\u53e6\u4e00\u4f4d\u73a9\u5bb6\u52a0\u5165`
  if (game.value.phase === 'FINISHED') return game.value.statusText
  if (!humanTurn.value) return initialDeployment.value ? '对手正在完成初始布阵，完成后才会开启争夺' : '雾隐执棋者正在推演局势…'
  if (initialDeployment.value && bothInitialSitesReady.value) return '双方场地已就位，先手骰将决定第一次争夺顺序'
  if (selectedCard.value) {
    if (selectedCard.value.cost > player.value.energy) return '灵力不足：可在右侧弃置这张牌'
    return targetMode.value === 'unit' ? '请选择可用的目标单位' : '请选择高亮的目标场地'
  }
  if (selectedUnit.value) return `当前射程 ${selectedUnit.value.attackRange || 1}：只能争夺金色高亮场地`
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
  clearInterval(countdownTimer)
  clearTimeout(pulseTimer)
  clearTimeout(revealTimer)
  clearTimeout(dismissRevealTimer)
  clearTimeout(phaseTimer)
  clearTimeout(combatTimer)
  clearTimeout(placementTimer)
  clearTimeout(aiTimer)
  clearInterval(initiativeRollTimer)
  clearTimeout(initiativeRevealTimer)
  clearTimeout(boardClickGuardTimer)
  clearTimeout(boardSettleTimer)
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
  if (previous && next) {
    if (next.players?.[next.activePlayerIndex]?.id !== localPlayerId.value && (next.statusText !== previous.statusText || JSON.stringify(next.sites) !== JSON.stringify(previous.sites))) {
      showAiFx(next.statusText || `${next.players?.[1]?.name || '对手'}正在行动`)
    }
    if (previous.phase === 'DEPLOY' && next.phase === 'CONTEST' && next.players?.[next.activePlayerIndex]?.id === localPlayerId.value) showPhaseFx('CONTEST')
    if (next.turnNumber > previous.turnNumber) {
      const oldHand = previous.players?.find(p => p.id === localPlayerId.value)?.hand || []
      const newCards = addedCardIds(oldHand, next.players?.find(p => p.id === localPlayerId.value)?.hand || []).map(id => cardMap.value[id]).filter(Boolean)
      if (newCards.length && next.players?.[next.activePlayerIndex]?.id === localPlayerId.value) showDrawFx(newCards)
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
  if (initiativeResolved) showInitiativeFx()
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
  if (e.key === 'Escape') clearSelection()
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

function zoomBattlefield(event) {
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

function startBoardPan(event) {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  if (event.target?.closest?.('button')) return
  const point = pointerPosition(event)
  activeBoardPointers.set(event.pointerId, point)
  clearTimeout(boardClickGuardTimer)
  clearTimeout(boardSettleTimer)
  if (activeBoardPointers.size === 1) {
    boardDragStart = { ...point, panX: boardPanX.value, panY: boardPanY.value }
    boardPinchStart = null
    boardDragFeedback = false
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
  boardPanX.value = boardDragStart.panX + dx
  boardPanY.value = boardDragStart.panY + dy
  constrainBoardPan()
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
      boardSettling.value = true
      haptic('release')
      clearTimeout(boardSettleTimer)
      boardSettleTimer = setTimeout(() => { boardSettling.value = false }, 220)
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
}

function chooseCard(card) {
  if (!humanTurn.value || busy.value || drawReveal.value || phaseFx.value || combatFx.value || initiativeFx.value || (initialDeployment.value && bothInitialSitesReady.value)) return
  selectedUnit.value = null
  const toggledOff = selectedCard.value?.id === card.id
  selectedCard.value = toggledOff ? null : card
  tone('select')
  if (toggledOff) return
  if (player.value.hand.length > 7) return notify('手牌超过上限，请先弃置一张手牌')
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
  if (selectedUnit.value) return game.value.phase === 'CONTEST' && canAttackSite(site)
  if (selectedCard.value?.type === 'SITE') return game.value.phase === 'DEPLOY' && (!site.ownerId || site.ownerId === localPlayerId.value)
  if (selectedCard.value?.type === 'UNIT') return game.value.phase === 'DEPLOY' && site.ownerId === localPlayerId.value && site.units.length < 2
  return false
}

function isSiteOutOfRange(site) {
  return Boolean(selectedUnit.value && site.ownerId && site.ownerId !== localPlayerId.value && !canAttackSite(site))
}

function isSourceSite(site) {
  return Boolean(selectedUnit.value && sourceSite(selectedUnit.value.instanceId)?.index === site.index)
}

async function selectSite(site) {
  if (!humanTurn.value || busy.value || drawReveal.value || phaseFx.value || combatFx.value || initiativeFx.value || (initialDeployment.value && bothInitialSitesReady.value)) return
  if (selectedCard.value && targetMode.value === 'site') {
    if (selectedCard.value.cost > player.value.energy) return notify('灵力不足，请改选或弃置这张牌')
    if (!isSiteTargetable(site)) {
      if (selectedCard.value.type === 'UNIT') return notify(site.ownerId !== localPlayerId.value ? '单位只能部署到己方场地' : '该场地已驻扎2个单位，可先撤离一个')
      return notify('不能直接覆盖敌方场地')
    }
    return play(selectedCard.value, { targetSiteIndex: site.index })
  }
  if (selectedUnit.value && game.value.phase === 'CONTEST') {
    if (!site.ownerId) return notify('无主场地需要用场地卡部署')
    if (site.ownerId === localPlayerId.value) return notify('请选择敌方场地')
    if (!canAttackSite(site)) {
      const distance = distanceForSite(site)
      return notify(`目标距离为 ${distance}，该单位射程只有 ${selectedUnit.value.attackRange || 1}`)
    }
    await resolveAttack(site)
  }
}

function selectUnit(unit) {
  if (!humanTurn.value || busy.value || drawReveal.value || phaseFx.value || combatFx.value) return
  if (selectedCard.value && targetMode.value === 'unit') {
    if (selectedCard.value.cost > player.value.energy) return notify('灵力不足，请改选或弃置这张牌')
    if (unit.ownerId !== targetUnitOwner.value) return notify(targetUnitOwner.value === localPlayerId.value ? '请选择己方单位' : '请选择敌方单位')
    return play(selectedCard.value, { targetUnitId: unit.instanceId })
  }
  if (game.value.phase !== 'CONTEST') return notify('部署阶段可点单位右上角撤离；进入争夺后才能选为攻击者')
  if (unit.ownerId !== localPlayerId.value) return notify('只能选择己方单位发起争夺')
  if (unit.exhausted || unit.sealed) return notify(unit.sealed ? '该单位正被封印' : '该单位本回合已行动')
  selectedUnit.value = selectedUnit.value?.instanceId === unit.instanceId ? null : unit
  selectedCard.value = null
  tone('select')
}

async function play(card, target) {
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
  combatFx.value = { attacker: { ...attacker }, target: { ...site }, attackPower, defense, stage: 'charge', result: '' }
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
  if (!playerReady.value) return notify('请先部署至少一张己方场地')
  clearSelection()
  await act(() => api.endTurn(game.value.id, localPlayerId.value), '双方初始场地已就位')
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
  clearSelection()
  await act(() => api.endTurn(game.value.id, localPlayerId.value), '回合交替')
}

async function retreatUnit(unit) {
  if (!humanTurn.value || game.value.phase !== 'DEPLOY' || busy.value) return
  await act(() => api.retreatUnit(game.value.id, localPlayerId.value, unit.instanceId), `撤离「${unit.name}」`)
  clearSelection()
}

async function discard(card) {
  if (!humanTurn.value || game.value.phase !== 'DEPLOY' || busy.value) return
  await act(() => api.discard(game.value.id, localPlayerId.value, card.id), `弃置「${card.name}」`)
  clearSelection()
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

function showAiFx(detail) {
  clearTimeout(aiTimer)
  const attack = detail.includes('争夺') || detail.includes('战力')
  const deploy = detail.includes('部署') || detail.includes('驻场')
  aiFx.value = { detail, kind: attack ? 'attack' : deploy ? 'deploy' : 'thinking' }
  tone(attack ? 'impact' : 'place')
  aiTimer = setTimeout(() => { aiFx.value = null }, 1150)
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
  showExitConfirm.value = true
  tone('warning')
}

function cancelExit() {
  showExitConfirm.value = false
  pendingExitTarget.value = '/'
}

async function confirmExit() {
  if (!game.value || exiting.value) return
  exiting.value = true
  error.value = ''
  try {
    const next = await api.leaveMatch(game.value.id, localPlayerId.value)
    syncGame(next)
    localStorage.setItem('fieldrealm-match-ban-until', String(Date.now() + 30_000))
    allowRouteLeave.value = true
    showExitConfirm.value = false
    await router.push(pendingExitTarget.value || '/')
  } catch (e) {
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
</script>

<template>
  <div class="battle-page" :class="{ 'is-busy': busy, 'contest-mode': game?.phase === 'CONTEST' }">
    <div v-if="loading" class="battle-loading"><LoaderCircle class="spin"/><b>正在展开九域棋盘</b><span>同步场地与卡组数据…</span></div>
    <div v-else-if="!game" class="battle-loading error-screen"><b>无法进入弈境</b><span>{{ error }}</span><button class="primary-button" @click="router.push('/')">返回大厅</button></div>
    <template v-else>
      <header class="battle-topbar">
        <button class="battle-brand" @click="requestExit('/')"><ArrowLeft :size="18"/><span class="brand-mark"><i></i><b></b></span><strong>场地弈境</strong></button>
        <div class="round-meter"><span>第 {{ Math.min(game.round, 8) }} / 8 回合</span><div><i :style="{ width: `${Math.min(game.turnNumber / 16 * 100, 100)}%` }"></i></div><b :class="{ urgent: phaseClockUrgent }">{{ phaseLabel }} <em v-if="phaseClockLabel">{{ phaseClockLabel }}</em></b></div>
        <div class="battle-tools"><button @click="soundOn = !soundOn" :aria-label="soundOn ? '关闭声音与触感' : '开启声音与触感'"><Volume2 v-if="soundOn"/><VolumeX v-else/></button><button @click="showLog = !showLog"><History/><span>战局记录</span></button><button @click="requestExit('/rules')"><BookOpen/><span>规则</span></button><button aria-label="退出对局" @click="requestExit('/')"><LogOut/></button></div>
      </header>

      <section class="opponent-strip player-strip rival-strip">
        <div class="combatant"><span class="combatant-avatar rival-avatar">{{ rival.avatar }}</span><div><small>{{ rival.title }}</small><b>{{ rival.name }}</b></div></div>
        <div class="resource-cluster"><span class="control-count">控制 <b>{{ controlled(rivalPlayerId) }}</b>/{{ game.sites.length }}</span><span class="score-orb rival-score"><small>积分</small><b>{{ rival.score }}</b></span><span class="hand-count"><Hand/>{{ rival.hand.length }}</span></div>
      </section>

      <main class="battlefield">
        <div class="field-mist mist-a"></div><div class="field-mist mist-b"></div>
        <div class="battle-lane-label"><span>战场态势</span><b>{{ game.phase === 'CONTEST' ? '目标锁定 · 选择你的攻击路线' : initialDeployment ? '初始布阵 · 双方场地就位后开启争夺' : '部署窗口 · 调整你的阵地' }}</b></div>
        <div v-if="initialDeployment" class="deployment-readiness" aria-label="双方初始场地部署状态">
          <span :class="{ ready: playerReady }"><CheckCircle2/>你方{{ playerReady ? '已就位' : '未布置' }}</span>
          <i></i>
          <span :class="{ ready: rivalReady }"><CheckCircle2/>对手{{ rivalReady ? '已就位' : '待布阵' }}</span>
        </div>
        <div
          ref="battlefieldViewport"
          class="battlefield-viewport"
          :class="{ 'is-panning': boardPanning, 'is-settling': boardSettling }"
          role="region"
          tabindex="0"
          aria-label="九域战场视野。手机可上下左右拖动，滚轮或双指可缩放，按 0 重置视野。"
          @wheel.prevent="zoomBattlefield"
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
                :active-player="active.id"
                :selected-unit-id="selectedUnit?.instanceId"
                :target-mode="targetMode"
                :targetable="isSiteTargetable(site)"
                :target-unit-owner="targetUnitOwner"
                :distance="distanceForSite(site)"
                :out-of-range="isSiteOutOfRange(site)"
                :source="isSourceSite(site)"
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
              </svg>
            </div>
          </div>
        </div>
        <div class="battlefield-view-controls" aria-label="战场视野控制">
          <span class="view-control-hint"><Move :size="13"/>上下左右拖拽</span>
          <button type="button" title="缩小视野（-）" aria-label="缩小战场视野" @click="changeBoardZoom(-.12)"><Minus/></button>
          <output aria-live="polite">{{ boardZoomLabel }}</output>
          <button type="button" title="放大视野（+）" aria-label="放大战场视野" @click="changeBoardZoom(.12)"><Plus/></button>
          <button type="button" title="重置视野（0）" aria-label="重置战场视野" @click="resetBoardView"><LocateFixed/></button>
        </div>
        <div class="battle-flow-hint"><span class="hint-dot"></span><span v-if="game.phase === 'DEPLOY'">{{ initialDeployment ? '先部署己方场地，再完成部署等待对手场地显现' : '从下方手牌开始：选卡后，点击场地放下它' }}</span><span v-else>金色高亮为射程内目标；中央天元与八个外域均相邻</span></div>
        <transition name="impact"><div v-if="pulse" class="impact-banner"><Sparkles/>{{ pulse }}</div></transition>
        <transition name="fade"><div v-if="error" class="battle-toast"><X :size="16"/>{{ error }}</div></transition>
        <transition name="impact"><div v-if="aiFx" class="ai-action-banner" :class="`ai-${aiFx.kind}`"><span class="ai-action-icon"><Swords v-if="aiFx.kind === 'attack'"/><Sparkles v-else/></span><div><small>OPPONENT ACTION</small><b>雾隐执棋者正在行动</b><p>{{ aiFx.detail }}</p></div></div></transition>
        <div v-if="placementFx" class="placement-flight" :class="`to-site-${placementFx.targetSiteIndex}`" :style="{ '--flight-color': placementFx.card ? '#63d2a5' : '#efc36f' }"><span class="flight-trail"></span><GameCard v-if="placementFx.card" class="flight-card" :card="placementFx.card" compact disabled/><span class="flight-label">{{ placementFx.card.name }}</span><Zap :size="18"/></div>
      </main>

      <section class="player-strip self-strip">
        <div class="combatant"><span class="combatant-avatar">{{ player.avatar }}</span><div><small>{{ player.title }}</small><b>{{ player.name }}</b></div></div>
        <div class="turn-guidance"><span :class="{ active: humanTurn }">{{ humanTurn ? '你的回合' : '等待对手' }}</span><p>{{ instruction }}</p><div class="action-guide"><span v-for="(step, index) in actionSteps" :key="step.label" :class="{ done: step.done, current: index === (selectedCard || selectedUnit ? 1 : 0) }"><i>{{ step.done ? '✓' : index + 1 }}</i>{{ step.label }}</span></div></div>
        <div class="resource-cluster"><span class="energy-pips" title="本回合灵力"><i v-for="n in 3" :key="n" :class="{ filled: n <= player.energy }">✦</i></span><span class="control-count">控制 <b>{{ controlled(localPlayerId) }}</b>/{{ game.sites.length }}</span><span class="score-orb"><small>积分</small><b>{{ player.score }}</b></span></div>
      </section>

      <section class="hand-area">
        <div v-if="player.hand.length > 7" class="discard-warning">手牌超过上限，请选中一张牌后点击「弃置选中牌」</div>
        <div class="hand-caption"><span>你的手牌</span><b>{{ hand.length }} / 7</b><small>点击卡牌查看可用目标</small></div>
        <TransitionGroup name="hand-card" tag="div" class="hand-cards">
          <GameCard v-for="(card, i) in hand" :key="`${card.id}-${i}`" :card="card" compact :selected="selectedCard?.id === card.id" :class="{ unaffordable: card.cost > player.energy }" :disabled="!humanTurn" @select="chooseCard"/>
        </TransitionGroup>
        <div class="turn-actions">
          <button v-if="selectedCard && humanTurn && game.phase === 'DEPLOY'" class="discard-selected" :disabled="busy" @click.stop="discardSelected"><Trash2/><span>弃置选中牌</span></button>
          <button v-if="game.phase === 'DEPLOY' && initialDeployment" class="phase-button ready-deployment" :disabled="!humanTurn || busy || player.hand.length > 7 || !playerReady" @click="completeInitialDeployment"><CheckCircle2/><span>完成部署</span><small>C</small></button>
          <button v-else-if="game.phase === 'DEPLOY'" class="phase-button" :disabled="!humanTurn || busy || player.hand.length > 7" @click="enterContest"><Swords/><span>进入争夺</span><small>C</small></button>
          <button v-else-if="game.phase === 'CONTEST'" class="phase-button end" :disabled="!humanTurn || busy" @click="endTurn"><ChevronRight/><span>结束回合</span><small>E</small></button>
        </div>
      </section>

      <transition name="drawer"><aside v-if="showLog" class="battle-log"><header><div><span>战局记录</span><b>第 {{ game.round }} 回合</b></div><button @click="showLog = false"><X/></button></header><ol><li v-for="(line, i) in game.log" :key="i"><i></i><span>{{ line }}</span></li></ol></aside></transition>

      <transition name="fade"><div v-if="showExitConfirm" class="exit-confirm-overlay" role="dialog" aria-modal="true" aria-labelledby="exit-confirm-title"><div class="exit-confirm-card"><span class="exit-warning-icon"><LogOut/></span><small>LEAVE MATCH</small><h2 id="exit-confirm-title">确认退出当前对局？</h2><p>退出后对手将直接获胜，你将在 <strong>30 秒</strong> 内无法创建、加入或匹配新对局。</p><div><button class="secondary-button" :disabled="exiting" @click="cancelExit">继续对局</button><button class="danger-button" :disabled="exiting" @click="confirmExit">{{ exiting ? '正在退出…' : '确认退出' }}</button></div></div></div></transition>

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

      <transition name="combat-overlay"><div v-if="combatFx" class="combat-overlay" :class="`combat-${combatFx.stage}`"><div class="combat-topline"><span>CONTEST RESOLUTION</span><b>场地争夺</b></div><div class="duel-scene"><div class="combat-side attacker-side"><span class="combat-avatar">{{ combatFx.attacker.name.slice(-1) }}</span><small>进攻单位</small><b>{{ combatFx.attacker.name }}</b><strong>{{ combatFx.attackPower }} <em>战力</em></strong></div><div class="combat-middle"><span class="energy-ring"></span><Swords :size="40"/><i></i><b>{{ combatFx.stage === 'charge' ? '蓄势冲锋' : combatFx.stage === 'clash' ? '交锋！' : '结算完成' }}</b></div><div class="combat-side defender-side"><span class="combat-site-glyph">◇</span><small>防守场地</small><b>{{ combatFx.target.name }}</b><strong>{{ combatFx.defense }} <em>守力</em></strong></div></div><div v-if="combatFx.stage === 'result'" class="combat-result"><Zap :size="17"/>{{ combatFx.result }}</div></div></transition>

      <div v-if="game.waitingForOpponent" class="victory-overlay waiting-overlay"><div class="victory-card"><span class="victory-sigil">&#9203;</span><small>ONLINE ROOM</small><h2>&#31561;&#24453;&#23545;&#25163;&#21152;&#20837;</h2><p>&#25226;&#25151;&#38388;&#21495;&#21457;&#32473;&#26379;&#21451;</p><div class="room-code">{{ game.id }}</div><button class="secondary-button" @click="router.push('/')">&#36820;&#22238;&#22823;&#21381;</button></div></div>
      <div v-if="game.phase === 'FINISHED'" class="victory-overlay"><div class="victory-card" :class="{ lost: game.winnerId !== localPlayerId }"><span class="victory-sigil">{{ game.winnerId === localPlayerId ? '✦' : '◇' }}</span><small>{{ game.victoryType }}</small><h2>{{ game.winnerId === localPlayerId ? '主动权尽在掌握' : '棋局仍有回响' }}</h2><p>{{ game.statusText }}</p><div class="final-score"><span><b>{{ player.score }}</b><small>{{ player.name }}</small></span><i>:</i><span><b>{{ rival.score }}</b><small>{{ rival.name }}</small></span></div><div v-if="game.ranked" class="rating-result" :class="{ positive: ratingDelta >= 0, negative: ratingDelta < 0 }"><small>本场天梯积分</small><strong>{{ ratingDirection }} {{ ratingDelta >= 0 ? `+${ratingDelta}` : ratingDelta }}</strong><span v-if="ratingAfter !== undefined">当前积分 {{ ratingAfter }}</span></div><div class="victory-actions"><button v-if="!game.ranked" class="primary-button" @click="rematch"><RotateCcw/>再弈一局</button><button class="secondary-button" @click="router.push('/')">返回大厅</button></div></div></div>
    </template>
  </div>
</template>
