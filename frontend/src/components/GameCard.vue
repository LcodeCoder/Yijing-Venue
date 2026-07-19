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

const meta = computed(() => typeMeta[props.card.type] || typeMeta.UNIT)
const detailStats = computed(() => {
  const stats = [`\u7075\u529b ${props.card.cost}`]
  if (props.card.type === 'UNIT') stats.push(`\u6218 ${props.card.power}`, `\u5b88 ${props.card.guard}`)
  if (props.card.type === 'SITE') stats.push(`\u5206 ${props.card.points}`, `\u5b88 ${props.card.guard}`)
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

function handleSelect(event) {
  // A mobile double tap opens details; keep the second tap from toggling the card selection.
  if (isTouchDevice() && event?.detail === 2) return
  detailsOpen.value = false
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

async function openDetails(event) {
  const rect = root.value?.getBoundingClientRect()
  detailAnchor = { x: event.clientX || rect?.right || 12, y: event.clientY || rect?.top || 12 }
  detailsOpen.value = true
  await nextTick()
  placeDetails()
}

function toggleDetails(event) {
  event.preventDefault()
  event.stopPropagation()
  if (detailsOpen.value) {
    detailsOpen.value = false
    return
  }
  openDetails(event)
}

function handleContextMenu(event) {
  event.preventDefault()
  if (!isTouchDevice()) toggleDetails(event)
}

function handleDoubleClick(event) {
  if (isTouchDevice()) toggleDetails(event)
}

function handleCardKeydown(event) {
  if ((event.shiftKey && event.key === 'F10') || event.key === 'ContextMenu') toggleDetails(event)
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
    :title="isTouchDevice() ? '\u53cc\u51fb\u67e5\u770b\u5361\u724c\u8be6\u60c5' : '\u53f3\u952e\u67e5\u770b\u5361\u724c\u8be6\u60c5'"
    @click="handleSelect"
    @dblclick="handleDoubleClick"
    @keydown="handleCardKeydown"
    @contextmenu="handleContextMenu"
  >
    <span class="card-frame">
      <span class="card-cost">{{ card.cost }}</span>
      <span class="card-art" :data-glyph="meta.glyph">
        <i class="orb orb-one"></i><i class="orb orb-two"></i><i class="sigil"></i>
      </span>
      <span class="card-copy">
        <span class="card-kicker">{{ rarityName[card.rarity] }} &#183; {{ meta.label }}</span>
        <strong>{{ card.name }}</strong>
        <span class="card-effect">{{ card.effect }}</span>
      </span>
      <span v-if="card.type === 'UNIT'" class="card-stats"><b>&#x6218; {{ card.power }}</b><b>&#x5b88; {{ card.guard }}</b></span>
      <span v-else-if="card.type === 'SITE'" class="card-stats"><b>&#x5206; {{ card.points }}</b><b>&#x5b88; {{ card.guard }}</b></span>
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
        aria-label="&#x5361;&#x724c;&#x8be6;&#x7ec6;&#x4fe1;&#x606f;"
        @contextmenu.prevent
      >
        <small>{{ rarityName[card.rarity] }} &#183; {{ meta.label }}</small>
        <strong>{{ card.name }}</strong>
        <p>{{ card.effect }}</p>
        <blockquote v-if="card.flavor" class="card-detail-flavor">&#x201c;{{ card.flavor }}&#x201d;</blockquote>
        <div v-if="detailStats.length" class="card-detail-stats">
          <b v-for="stat in detailStats" :key="stat">{{ stat }}</b>
        </div>
        <div v-if="card.tags?.length" class="card-detail-tags">
          <span v-for="tag in card.tags" :key="tag">#{{ tag }}</span>
        </div>
        <em>&#x70b9;&#x51fb;&#x7a7a;&#x767d;&#x5904;&#x6216;&#x6309;&#x45;&#x73;&#x63;&#x5173;&#x95ed;</em>
      </div>
    </transition>
  </Teleport>
</template>
