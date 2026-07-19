<script setup>
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Layers3, Sparkles, Shield, Sword, AlertTriangle } from 'lucide-vue-next'
import { api } from '../services/api'
import GameCard from '../components/GameCard.vue'

const cards = ref([])
const deckIds = ref([])
const selected = ref(null)
const archetypes = ref([])
const rules = ref(null)
const tags = ref({})
const currentArchetype = ref('balanced')

onMounted(async () => {
  const [all, arch, rule, tagMap] = await Promise.all([
    api.cards(),
    api.archetypes().catch(() => []),
    api.deckRules().catch(() => null),
    api.cardTags().catch(() => ({}))
  ])
  cards.value = all
  archetypes.value = arch
  rules.value = rule
  tags.value = tagMap
  await loadArchetype('balanced')
})

async function loadArchetype(id) {
  currentArchetype.value = id
  const deck = await api.starterDeck(id)
  deckIds.value = deck.cards
  selected.value = null
}

const entries = computed(() => cards.value.map(card => ({
  card,
  count: deckIds.value.filter(id => id === card.id).length,
  archetypes: tags.value[card.id] || []
})).filter(e => e.count))

const stats = computed(() => {
  const total = deckIds.value.length
  const sites = deckIds.value.filter(id => cards.value.find(c => c.id === id)?.type === 'SITE').length
  const units = deckIds.value.filter(id => cards.value.find(c => c.id === id)?.type === 'UNIT').length
  const ssr = deckIds.value.filter(id => cards.value.find(c => c.id === id)?.rarity === 'SSR').length
  const copiesOk = entries.value.every(e => e.count <= (rules.value?.maxCopies || 2) || e.card.rarity === 'SSR')
  const ssrOk = ssr <= (rules.value?.maxSsr || 1)
  const siteOk = sites >= (rules.value?.minSites || 10)
  const unitOk = units >= (rules.value?.minUnits || 12)
  const sizeOk = total === (rules.value?.deckSize || 40)
  const valid = sizeOk && siteOk && unitOk && ssrOk
  const avg = total ? (deckIds.value.reduce((n, id) => n + (cards.value.find(c => c.id === id)?.cost || 0), 0) / total).toFixed(1) : 0
  return { total, sites, units, ssr, avg, valid, copiesOk, ssrOk, siteOk, unitOk, sizeOk }
})
</script>

<template>
  <div class="content-page section-wrap">
    <header class="page-heading split-heading">
      <div>
        <span>卡组构筑</span>
        <h1>主题卡组与构筑约束</h1>
        <p>{{ rules?.description || '卡组须满40张；同名最多2张；SSR最多1张；场地至少10张、单位至少12张。' }}</p>
      </div>
      <div class="valid-badge" :class="{ invalid: !stats.valid }">
        <CheckCircle2 v-if="stats.valid"/><AlertTriangle v-else/>
        {{ stats.valid ? '卡组合法，可投入对局' : '卡组未满足约束' }}
      </div>
    </header>

    <section class="archetype-picker">
      <button
        v-for="a in archetypes"
        :key="a.id"
        type="button"
        :class="{ active: currentArchetype === a.id }"
        @click="loadArchetype(a.id)"
      >
        <b>{{ a.name }}</b>
        <small>{{ a.winPath }}</small>
        <span>{{ a.focus }}</span>
      </button>
    </section>

    <section class="deck-stats">
      <article><Layers3/><div><b>{{ stats.total }} / {{ rules?.deckSize || 40 }}</b><small>卡组总数</small></div></article>
      <article><Sparkles/><div><b>{{ stats.sites }}</b><small>场地 ≥ {{ rules?.minSites || 10 }}</small></div></article>
      <article><Sword/><div><b>{{ stats.units }}</b><small>单位 ≥ {{ rules?.minUnits || 12 }}</small></div></article>
      <article><Shield/><div><b>{{ stats.avg }}</b><small>平均灵力 · SSR {{ stats.ssr }}/{{ rules?.maxSsr || 1 }}</small></div></article>
    </section>

    <div class="deck-layout">
      <section class="deck-list glass-panel">
        <div class="panel-title">
          <div><span>当前卡组</span><b>{{ archetypes.find(a => a.id === currentArchetype)?.name || '主题组' }}</b></div>
          <small>点击预览 · 图鉴可看流派标签</small>
        </div>
        <button
          v-for="entry in entries"
          :key="entry.card.id"
          class="deck-row"
          :class="{ active: selected?.id === entry.card.id }"
          @click="selected = entry.card"
        >
          <span class="deck-glyph">{{ entry.card.type === 'SITE' ? '◇' : entry.card.type === 'UNIT' ? '✦' : entry.card.type === 'SPELL' ? '⌁' : '✺' }}</span>
          <span>
            <b>{{ entry.card.name }}</b>
            <small>{{ entry.card.effect }}{{ entry.archetypes.length ? ' · 流派 ' + entry.archetypes.join('/') : '' }}</small>
          </span>
          <em>{{ entry.card.cost }} 灵力</em>
          <strong>×{{ entry.count }}</strong>
        </button>
      </section>
      <aside class="deck-preview">
        <GameCard v-if="selected" :card="selected"/>
        <div v-else class="preview-placeholder">
          <Layers3/>
          <b>选择卡牌查看详情</b>
          <p>同名最多{{ rules?.maxCopies || 2 }}张，SSR 最多{{ rules?.maxSsr || 1 }}张。首页可选主题卡组直接开打。</p>
        </div>
        <div class="curve-panel glass-panel">
          <span>灵力曲线</span>
          <div class="curve-bars">
            <i
              v-for="cost in [1, 2, 3]"
              :key="cost"
              :style="{ height: `${30 + deckIds.filter(id => cards.find(c => c.id === id)?.cost === cost).length * 4}px` }"
            >
              <b>{{ deckIds.filter(id => cards.find(c => c.id === id)?.cost === cost).length }}</b>
              <small>{{ cost }}</small>
            </i>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>
