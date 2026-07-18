<script setup>
import { computed, onMounted, ref } from 'vue'
import { Search, SlidersHorizontal } from 'lucide-vue-next'
import { api } from '../services/api'
import GameCard from '../components/GameCard.vue'
const cards = ref([]), loading = ref(true), search = ref(''), type = ref('ALL'), rarity = ref('ALL')
onMounted(async()=>{ try { cards.value = await api.cards() } finally { loading.value=false } })
const filtered = computed(()=>cards.value.filter(c => (type.value==='ALL'||c.type===type.value) && (rarity.value==='ALL'||c.rarity===rarity.value) && (!search.value||`${c.name}${c.effect}${c.tags.join('')}`.includes(search.value))))
const types=[['ALL','全部'],['SITE','场地'],['UNIT','单位'],['SPELL','术式'],['SECRET','秘策']]
</script>
<template>
  <div class="content-page section-wrap">
    <header class="page-heading"><span>藏品图鉴</span><h1>弈境万象，尽收于此</h1><p>浏览当前「雾海纪元」全部卡牌。每张卡都服务于占场、控局与夺取主动权。</p></header>
    <section class="collection-toolbar glass-panel">
      <label class="search-box"><Search :size="18"/><input v-model="search" placeholder="搜索卡名、效果或词条"/></label>
      <div class="filter-tabs"><button v-for="t in types" :key="t[0]" :class="{active:type===t[0]}" @click="type=t[0]">{{t[1]}}</button></div>
      <label class="select-box"><SlidersHorizontal :size="16"/><select v-model="rarity"><option value="ALL">全部稀有度</option><option value="C">普通</option><option value="R">稀有</option><option value="SR">超稀有</option><option value="SSR">极致秘策</option></select></label>
    </section>
    <div class="result-line">已收录 <b>{{ filtered.length }}</b> 张卡牌</div>
    <section class="card-gallery" :class="{ loading }"><GameCard v-for="card in filtered" :key="card.id" :card="card"/><div v-if="!loading&&!filtered.length" class="empty-state">没有找到符合条件的卡牌。</div></section>
  </div>
</template>
