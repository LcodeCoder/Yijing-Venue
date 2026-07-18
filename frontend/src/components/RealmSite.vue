<script setup>
import { computed } from 'vue'
import { Shield, Star, Crown } from 'lucide-vue-next'
import UnitChip from './UnitChip.vue'

const props = defineProps({
  site: Object,
  players: Array,
  activePlayer: String,
  selectedUnitId: String,
  targetMode: String,
  targetable: Boolean,
  targetUnitOwner: String,
  impacting: Boolean,
  canRetreat: Boolean,
  distance: Number,
  outOfRange: Boolean,
  source: Boolean
})
const emit = defineEmits(['select-site', 'select-unit', 'retreat-unit'])
const ownerIndex = computed(() => props.site.ownerId ? props.players.findIndex(p => p.id === props.site.ownerId) : -1)
const ownerClass = computed(() => ownerIndex.value === 0 ? 'owner-player' : ownerIndex.value === 1 ? 'owner-rival' : 'owner-neutral')
const totalGuard = computed(() => props.site.baseGuard + props.site.units.filter(u => !u.sealed).reduce((n, u) => n + u.guard, 0))
const unitCanTarget = unit => props.targetMode === 'unit' && (!props.targetUnitOwner || unit.ownerId === props.targetUnitOwner)
</script>

<template>
  <div
    class="realm-site"
    :class="[ownerClass, { core: site.core, targetable, impacting, 'out-of-range': outOfRange, 'source-site': source }]"
    role="button"
    tabindex="0"
    @click="emit('select-site', site)"
    @keydown.enter="emit('select-site', site)"
    @keydown.space.prevent="emit('select-site', site)"
  >
    <span class="site-aura"></span>
    <span class="site-topline">
      <span class="site-position"><Crown v-if="site.core" :size="13"/>{{ site.position }}</span>
      <span class="site-owner">{{ ownerIndex >= 0 ? players[ownerIndex].name : '无主之地' }}</span>
    </span>
    <span v-if="distance !== undefined && distance !== null" class="site-distance" :class="{ blocked: outOfRange, origin: source }">
      {{ source ? '起点' : `距${distance}` }}
    </span>
    <span class="site-center">
      <span class="site-emblem"><span class="site-glyph">{{ site.core ? '✦' : '◇' }}</span></span>
      <span class="site-copy"><strong>{{ site.name }}</strong><span class="site-effect">{{ site.effect }}</span></span>
    </span>
    <span class="site-metrics">
      <b><Star :size="13"/>{{ (site.basePoints || 0) * (site.core ? 2 : 1) }}</b>
      <b><Shield :size="13"/>{{ totalGuard }}</b>
      <em class="site-capacity">{{ site.units.length }}/2</em>
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
