<script setup>
import { computed } from 'vue'
import { Crosshair, Shield, Sword, Undo2 } from 'lucide-vue-next'

const props = defineProps({
  unit: Object,
  activePlayer: String,
  selected: Boolean,
  targetable: Boolean,
  canRetreat: Boolean
})
const emit = defineEmits(['select', 'retreat'])

const effectiveGuard = computed(() => {
  const u = props.unit
  return Math.max(0, (u.guard || 0) + ((u.rootedTurns || 0) >= 2 ? 1 : 0) - (u.shaken ? 1 : 0))
})

const statuses = computed(() => {
  const u = props.unit
  const list = []
  if (u.sealed) list.push({ key: 'sealed', short: '封', label: '封印', tip: '封印：无法行动', tone: 'danger' })
  if (u.shaken) list.push({ key: 'shaken', short: '摇', label: '动摇', tip: '动摇：守力-1，不可进攻', tone: 'warn' })
  if (u.exhausted) list.push({ key: 'exhausted', short: '动', label: '已行动', tip: '已行动：本回合不能再争夺', tone: 'muted' })
  if (u.marching) list.push({ key: 'marching', short: '新', label: '新驻', tip: '新驻：本回合刚部署', tone: 'info' })
  if ((u.rootedTurns || 0) >= 2) list.push({ key: 'rooted', short: '根', label: '扎根', tip: '扎根：守力+1', tone: 'good' })
  if ((u.powerBuff || 0) > 0) list.push({ key: 'power', short: `+${u.powerBuff}`, label: `战+${u.powerBuff}`, tip: '战力增幅', tone: 'buff' })
  if ((u.rangeBuff || 0) > 0) list.push({ key: 'range', short: `射${u.rangeBuff}`, label: `射+${u.rangeBuff}`, tip: '射程增幅', tone: 'range' })
  if ((u.guardBuff || 0) > 0) list.push({ key: 'guard', short: `守${u.guardBuff}`, label: `守+${u.guardBuff}`, tip: '守力增幅', tone: 'good' })
  return list
})

const statusSummary = computed(() => statuses.value.map(s => s.label).join(' · ') || '就绪')
</script>

<template>
  <div
    class="unit-chip"
    :class="[{
      selected,
      targetable,
      exhausted: unit.exhausted,
      sealed: unit.sealed,
      shaken: unit.shaken,
      marching: unit.marching,
      rooted: (unit.rootedTurns || 0) >= 2
    }, unit.ownerId]"
    role="button"
    tabindex="0"
    :title="`${unit.name} · ${statusSummary}`"
    :aria-label="`${unit.name}，${statusSummary}`"
    @click.stop="emit('select', unit)"
    @keydown.enter.stop="emit('select', unit)"
    @keydown.space.stop.prevent="emit('select', unit)"
  >
    <span class="unit-avatar">{{ unit.name.slice(-1) }}</span>
    <span class="unit-mid">
      <span class="unit-name">{{ unit.name }}</span>
      <span v-if="statuses.length" class="unit-status-inline" role="list" aria-label="单位状态">
        <span
          v-for="s in statuses"
          :key="s.key"
          class="unit-tag"
          :class="`tone-${s.tone}`"
          role="listitem"
          :title="s.tip"
        >{{ s.short }}</span>
      </span>
    </span>
    <span class="unit-values">
      <b title="战力"><Sword :size="11"/>{{ unit.power }}</b>
      <b title="守力"><Shield :size="11"/>{{ effectiveGuard }}</b>
      <b class="unit-range" title="射程"><Crosshair :size="11"/>{{ unit.attackRange || 1 }}</b>
    </span>
    <button
      v-if="canRetreat && unit.ownerId === activePlayer"
      class="unit-retreat"
      type="button"
      title="撤离单位至弃牌堆"
      aria-label="撤离单位"
      @click.stop="emit('retreat', unit)"
    ><Undo2 :size="12"/></button>
  </div>
</template>
