<script setup>
import { computed, onMounted, ref } from 'vue'
import { Search, SlidersHorizontal } from 'lucide-vue-next'
import { api } from '../services/api'
import GameCard from '../components/GameCard.vue'

const cards = ref([])
const tags = ref({})
const loading = ref(true)
const search = ref('')
const type = ref('ALL')
const rarity = ref('ALL')
const archNames = {
  bastion: '壁垒',
  ranger: '游猎',
  forge: '锻场',
  draw: '运营',
  dominion: '绝杀',
  balanced: '均衡'
}

onMounted(async () => {
  try {
    const [all, tagMap] = await Promise.all([api.cards(), api.cardTags().catch(() => ({}))])
    cards.value = all
    tags.value = tagMap
  } finally {
    loading.value = false
  }
})

const filtered = computed(() => cards.value.filter(c => {
  const tagText = (tags.value[c.id] || []).map(t => archNames[t] || t).join('')
  return (type.value === 'ALL' || c.type === type.value)
    && (rarity.value === 'ALL' || c.rarity === rarity.value)
    && (!search.value || `${c.name}${c.effect}${(c.tags || []).join('')}${tagText}`.includes(search.value))
}))

const types = [['ALL', '全部'], ['SITE', '场地'], ['UNIT', '单位'], ['SPELL', '术式'], ['SECRET', '秘策']]
function cardTags(id) {
  return (tags.value[id] || []).map(t => archNames[t] || t)
}
</script>

<template>
  <div class="content-page section-wrap">
    <header class="page-heading">
      <span>藏品图鉴</span>
      <h1>弈境万象，尽收于此</h1>
      <p>浏览「雾海纪元」全部卡牌。关键牌标注适合流派，便于构筑壁垒、游猎、锻场、运营与绝杀体系。</p>
    </header>
    <section class="collection-toolbar glass-panel">
      <label class="search-box"><Search :size="18"/><input v-model="search" placeholder="搜索卡名、效果或流派"/></label>
      <div class="filter-tabs"><button v-for="t in types" :key="t[0]" :class="{ active: type === t[0] }" @click="type = t[0]">{{ t[1] }}</button></div>
      <label class="select-box">
        <SlidersHorizontal :size="16"/>
        <select v-model="rarity">
          <option value="ALL">全部稀有度</option>
          <option value="C">普通</option>
          <option value="R">稀有</option>
          <option value="SR">超稀有</option>
          <option value="SSR">极致秘策</option>
        </select>
      </label>
    </section>
    <div class="result-line">已收录 <b>{{ filtered.length }}</b> 张卡牌</div>
    <section class="card-gallery" :class="{ loading }">
      <div v-for="card in filtered" :key="card.id" class="gallery-card-wrap">
        <GameCard :card="card"/>
        <div v-if="cardTags(card.id).length" class="card-arch-tags">
          <span v-for="t in cardTags(card.id)" :key="t">{{ t }}</span>
        </div>
      </div>
      <div v-if="!loading && !filtered.length" class="empty-state">没有找到符合条件的卡牌。</div>
    </section>
  </div>
</template>
