<script setup>
import { Crosshair, Shield, Sword, LockKeyhole, Check, Undo2 } from 'lucide-vue-next'

defineProps({ unit: Object, activePlayer: String, selected: Boolean, targetable: Boolean, canRetreat: Boolean })
const emit = defineEmits(['select', 'retreat'])
</script>

<template>
  <div
    class="unit-chip"
    :class="[{ selected, targetable, exhausted: unit.exhausted, sealed: unit.sealed }, unit.ownerId]"
    role="button"
    tabindex="0"
    @click.stop="emit('select', unit)"
    @keydown.enter.stop="emit('select', unit)"
    @keydown.space.stop.prevent="emit('select', unit)"
  >
    <span class="unit-avatar">{{ unit.name.slice(-1) }}</span>
    <span class="unit-name">{{ unit.name }}</span>
    <span class="unit-values">
      <b><Sword :size="11"/>{{ unit.power }}</b>
      <b><Shield :size="11"/>{{ unit.guard }}</b>
      <b class="unit-range"><Crosshair :size="11"/>{{ unit.attackRange || 1 }}</b>
    </span>
    <button
      v-if="canRetreat && unit.ownerId === activePlayer"
      class="unit-retreat"
      type="button"
      title="撤离单位至弃牌堆"
      aria-label="撤离单位"
      @click.stop="emit('retreat', unit)"
    ><Undo2 :size="12"/></button>
    <LockKeyhole v-if="unit.sealed" class="unit-state" :size="15"/>
    <Check v-else-if="unit.exhausted" class="unit-state" :size="15"/>
  </div>
</template>
