<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { typeMeta, rarityClass, rarityName } from '../data/cardMeta'

const props = defineProps({
  card: { type: Object, required: true },
  compact: Boolean,
  selected: Boolean,
  disabled: Boolean
})
const emit = defineEmits(['select'])

const root = ref(null)
const detailPopup = ref(null)
const detailsOpen = ref(false)
const detailPosition = ref({ left: 12, top: 12 })
let detailAnchor = { x: 12, y: 12 }

/** 触摸：长按出详情；滑动超过阈值则当作翻牌，取消选中 */
const LONG_PRESS_MS = 420
const MOVE_CANCEL_PX = 12
let longPressTimer = null
let pressStart = null
let pressMoved = false
let suppressClick = false

const meta = computed(() => typeMeta[props.card.type] || typeMeta.UNIT)
const detailStats = computed(() => {
  const stats = [`灵力 ${props.card.cost}`]
  if (props.card.type === 'UNIT') stats.push(`战 ${props.card.power}`, `守 ${props.card.guard}`)
  if (props.card.type === 'SITE') stats.push(`分 ${props.card.points}`, `守 ${props.card.guard}`)
  return stats
})
const detailStyle = computed(() => ({
  left: `${detailPosition.value.left}px`,
  top: `${detailPosition.value.top}px`,
  '--type-color': meta.value.color
}))

function isTouchDevice() {
  return Boolean(window.matchMedia?.('(hover: none) and (pointer: coarse)').matches || navigator.maxTouchPoints > 0)
}

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function handleSelect(event) {
  // 长按已打开详情 / 滑动翻牌后，吞掉随后的 click，避免误选
  if (suppressClick) {
    suppressClick = false
    event.preventDefault()
    event.stopPropagation()
    return
  }
  if (detailsOpen.value) {
    detailsOpen.value = false
    return
  }
  if (!props.disabled) emit('select', props.card)
}

function placeDetails() {
  const popup = detailPopup.value
  if (!popup) return
  const gap = 12
  const viewportPadding = 12
  const bounds = popup.getBoundingClientRect()
  let left = detailAnchor.x + gap
  let top = detailAnchor.y + gap

  if (left + bounds.width > window.innerWidth - viewportPadding) left = detailAnchor.x - bounds.width - gap
  if (top + bounds.height > window.innerHeight - viewportPadding) top = detailAnchor.y - bounds.height - gap

  detailPosition.value = {
    left: Math.max(viewportPadding, Math.min(left, window.innerWidth - bounds.width - viewportPadding)),
    top: Math.max(viewportPadding, Math.min(top, window.innerHeight - bounds.height - viewportPadding))
  }
}

async function openDetails(clientX, clientY) {
  const rect = root.value?.getBoundingClientRect()
  detailAnchor = {
    x: clientX || rect?.right || 12,
    y: clientY || rect?.top || 12
  }
  detailsOpen.value = true
  await nextTick()
  placeDetails()
}

function handlePointerDown(event) {
  // 仅主触点；鼠标右键留给 contextmenu
  if (event.pointerType === 'mouse' && event.button !== 0) return
  pressMoved = false
  pressStart = { x: event.clientX, y: event.clientY }
  clearLongPress()

  // 触屏：长按看详情（不再用双击，避免误触）
  if (event.pointerType === 'touch' || event.pointerType === 'pen' || isTouchDevice()) {
    longPressTimer = setTimeout(() => {
      longPressTimer = null
      suppressClick = true
      openDetails(pressStart.x, pressStart.y)
      try { navigator.vibrate?.(12) } catch { /* ignore */ }
    }, LONG_PRESS_MS)
  }
}

function handlePointerMove(event) {
  if (!pressStart) return
  const dx = event.clientX - pressStart.x
  const dy = event.clientY - pressStart.y
  if (Math.hypot(dx, dy) >= MOVE_CANCEL_PX) {
    pressMoved = true
    clearLongPress()
  }
}

function handlePointerUp() {
  clearLongPress()
  // 若发生滑动，阻止随后 click 选中卡牌（便于横向翻手牌）
  if (pressMoved) suppressClick = true
  pressStart = null
  pressMoved = false
}

function handlePointerCancel() {
  clearLongPress()
  pressStart = null
  pressMoved = false
}

function handleContextMenu(event) {
  // 桌面右键详情；触屏屏蔽系统菜单，详情改用长按
  event.preventDefault()
  if (event.pointerType === 'touch' || isTouchDevice()) return
  openDetails(event.clientX, event.clientY)
}

function handleCardKeydown(event) {
  if ((event.shiftKey && event.key === 'F10') || event.key === 'ContextMenu') {
    event.preventDefault()
    const rect = root.value?.getBoundingClientRect()
    openDetails(rect?.right || 12, rect?.top || 12)
  }
}

function handleDocumentPointerDown(event) {
  if (!detailsOpen.value) return
  if (root.value?.contains(event.target) || detailPopup.value?.contains(event.target)) return
  detailsOpen.value = false
}

function handleDocumentKeydown(event) {
  if (event.key === 'Escape') detailsOpen.value = false
}

function closeDetails() {
  detailsOpen.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
  window.addEventListener('resize', closeDetails)
  window.addEventListener('scroll', closeDetails, true)
})

onBeforeUnmount(() => {
  clearLongPress()
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
  window.removeEventListener('resize', closeDetails)
  window.removeEventListener('scroll', closeDetails, true)
})
</script>

<template>
  <button
    ref="root"
    type="button"
    class="game-card"
    :class="[rarityClass(card.rarity), { compact, selected, disabled, 'details-open': detailsOpen }]"
    :data-type="card.type"
    :style="{ '--type-color': meta.color }"
    :aria-pressed="selected"
    :aria-disabled="disabled"
    :aria-expanded="detailsOpen"
    aria-haspopup="dialog"
    title="单击选择 · 长按查看详情（电脑可右键）"
    @click="handleSelect"
    @pointerdown="handlePointerDown"
    @pointermove="handlePointerMove"
    @pointerup="handlePointerUp"
    @pointercancel="handlePointerCancel"
    @pointerleave="handlePointerCancel"
    @keydown="handleCardKeydown"
    @contextmenu="handleContextMenu"
  >
    <span class="card-frame">
      <span class="card-cost">{{ card.cost }}</span>
      <span class="card-art" :data-glyph="meta.glyph">
        <i class="orb orb-one"></i><i class="orb orb-two"></i><i class="sigil"></i>
      </span>
      <span class="card-copy">
        <span class="card-kicker">{{ rarityName[card.rarity] }} · {{ meta.label }}</span>
        <strong>{{ card.name }}</strong>
        <span class="card-effect">{{ card.effect }}</span>
      </span>
      <span v-if="card.type === 'UNIT'" class="card-stats"><b>战 {{ card.power }}</b><b>守 {{ card.guard }}</b></span>
      <span v-else-if="card.type === 'SITE'" class="card-stats"><b>分 {{ card.points }}</b><b>守 {{ card.guard }}</b></span>
    </span>
  </button>

  <Teleport to="body">
    <transition name="card-detail-pop">
      <div
        v-if="detailsOpen"
        ref="detailPopup"
        class="card-detail-popup"
        :style="detailStyle"
        role="dialog"
        aria-label="卡牌详细信息"
        @contextmenu.prevent
        @click.stop
      >
        <small>{{ rarityName[card.rarity] }} · {{ meta.label }}</small>
        <strong>{{ card.name }}</strong>
        <p>{{ card.effect }}</p>
        <blockquote v-if="card.flavor" class="card-detail-flavor">“{{ card.flavor }}”</blockquote>
        <div v-if="detailStats.length" class="card-detail-stats">
          <b v-for="stat in detailStats" :key="stat">{{ stat }}</b>
        </div>
        <div v-if="card.tags?.length" class="card-detail-tags">
          <span v-for="tag in card.tags" :key="tag">#{{ tag }}</span>
        </div>
        <em>点空白处或按 Esc 关闭 · 手机可长按查看</em>
      </div>
    </transition>
  </Teleport>
</template>
