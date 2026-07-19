<script setup>
import { computed } from 'vue'
import { Shield, Star, Crown } from 'lucide-vue-next'
import UnitChip from './UnitChip.vue'

const props = defineProps({
  site: Object,
  players: Array,
  localPlayerId: String,
  activePlayer: String,
  selectedUnitId: String,
  targetMode: String,
  targetable: Boolean,
  targetUnitOwner: String,
  impacting: Boolean,
  canRetreat: Boolean,
  distance: Number,
  outOfRange: Boolean,
  source: Boolean,
  inRangeLine: Boolean
})
const emit = defineEmits(['select-site', 'select-unit', 'retreat-unit'])
const ownerBadge = computed(() => {
  if (!props.site.ownerId) return { text: '空', cls: 'owner-neutral' }
  if (props.localPlayerId && props.site.ownerId === props.localPlayerId) return { text: '己', cls: 'owner-player' }
  const idx = props.players?.findIndex(p => p.id === props.site.ownerId) ?? -1
  if (idx === 0) return { text: '己', cls: 'owner-player' }
  if (idx === 1) return { text: '敌', cls: 'owner-rival' }
  return props.site.ownerId === props.players?.[0]?.id
    ? { text: 'P1', cls: 'owner-player' }
    : { text: '敌', cls: 'owner-rival' }
})
const ownerClass = computed(() => ownerBadge.value.cls)
const totalGuard = computed(() => {
  if (typeof props.site.totalGuard === 'function') return props.site.totalGuard()
  const base = props.site.baseGuard || 0
  const units = (props.site.units || []).filter(u => !u.sealed)
  const unitGuard = units.reduce((n, u) => n + Math.max(0, (u.guard || 0) + ((u.rootedTurns || 0) >= 2 ? 1 : 0) - (u.shaken ? 1 : 0)), 0)
  return base + unitGuard + (props.site.adjacencyGuardBonus || 0)
})
const unitCanTarget = unit => props.targetMode === 'unit' && (!props.targetUnitOwner || unit.ownerId === props.targetUnitOwner)
const detailOpen = computed(() => false)
</script>

<template>
  <div
    class="realm-site"
    :class="[ownerClass, {
      core: site.core,
      frontier: site.frontier,
      targetable,
      impacting,
      'out-of-range': outOfRange,
      'source-site': source,
      'in-range-line': inRangeLine
    }]"
    role="button"
    tabindex="0"
    :title="site.effect"
    @click="emit('select-site', site)"
    @keydown.enter="emit('select-site', site)"
    @keydown.space.prevent="emit('select-site', site)"
  >
    <span class="site-aura"></span>
    <span class="owner-badge" :class="ownerClass">{{ ownerBadge.text }}</span>
    <span class="site-topline">
      <span class="site-position"><Crown v-if="site.core" :size="13"/>{{ site.position }}{{ site.frontier ? '·边' : '' }}</span>
      <span class="site-owner">{{ site.ownerId ? (players.find(p => p.id === site.ownerId)?.name || '有主') : '无主之地' }}</span>
    </span>
    <span v-if="distance !== undefined && distance !== null" class="site-distance" :class="{ blocked: outOfRange, origin: source }">
      {{ source ? '起点' : `距${distance}` }}
    </span>
    <span class="site-center">
      <span class="site-emblem"><span class="site-glyph">{{ site.core ? '✦' : '◇' }}</span></span>
      <span class="site-copy">
        <strong>{{ site.name }}</strong>
        <span class="site-effect">{{ site.effect }}</span>
      </span>
    </span>
    <span class="site-metrics">
      <b><Star :size="13"/>{{ site.frontier ? 0 : (site.basePoints || 0) * (site.core ? 2 : 1) }}{{ site.core ? '×2' : '' }}</b>
      <b><Shield :size="13"/>{{ totalGuard }}</b>
      <em class="site-capacity">{{ site.units.length }}/2</em>
      <em v-if="site.adjacencyGuardBonus" class="adj-bonus">邻+{{ site.adjacencyGuardBonus }}</em>
      <em v-if="site.effectCode === 'FORTRESS' && site.fortressHits">突破 {{ site.fortressHits }}/2</em>
    </span>
    <TransitionGroup v-if="site.units.length" name="unit-list" tag="span" class="units-row">
      <UnitChip
        v-for="unit in site.units"
        :key="unit.instanceId"
        :unit="unit"
        :active-player="activePlayer"
        :selected="unit.instanceId === selectedUnitId"
        :targetable="unitCanTarget(unit)"
        :can-retreat="canRetreat"
        @select="emit('select-unit', $event)"
        @retreat="emit('retreat-unit', $event)"
      />
    </TransitionGroup>
    <span v-else class="empty-slots"><i></i><i></i></span>
  </div>
</template>
