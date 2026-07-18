<script setup>
import { computed } from 'vue'
import { typeMeta, rarityClass, rarityName } from '../data/cardMeta'
const props = defineProps({ card: { type: Object, required: true }, compact: Boolean, selected: Boolean, disabled: Boolean })
const emit = defineEmits(['select'])
const meta = computed(() => typeMeta[props.card.type] || typeMeta.UNIT)
</script>

<template>
  <button class="game-card" :class="[rarityClass(card.rarity), { compact, selected, disabled }]" :style="{ '--type-color': meta.color }" @click="emit('select', card)" :aria-pressed="selected">
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
  </button>
</template>
