<script setup>
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Layers3, Sparkles, Shield, Sword } from 'lucide-vue-next'
import { api } from '../services/api'
import GameCard from '../components/GameCard.vue'
const cards=ref([]), deckIds=ref([]), selected=ref(null)
onMounted(async()=>{ const [all,deck]=await Promise.all([api.cards(),api.starterDeck()]);cards.value=all;deckIds.value=deck.cards })
const entries=computed(()=>cards.value.map(card=>({card,count:deckIds.value.filter(id=>id===card.id).length})).filter(e=>e.count))
const stats=computed(()=>({total:deckIds.value.length,sites:deckIds.value.filter(id=>cards.value.find(c=>c.id===id)?.type==='SITE').length,units:deckIds.value.filter(id=>cards.value.find(c=>c.id===id)?.type==='UNIT').length,avg:deckIds.value.length?(deckIds.value.reduce((n,id)=>n+(cards.value.find(c=>c.id===id)?.cost||0),0)/deckIds.value.length).toFixed(1):0}))
</script>
<template>
  <div class="content-page section-wrap">
    <header class="page-heading split-heading"><div><span>卡组构筑</span><h1>五域初阵</h1><p>平衡场地供给、驻场战力与瞬发反制，构筑属于你的控场节奏。</p></div><div class="valid-badge"><CheckCircle2/>卡组合法，可投入对局</div></header>
    <section class="deck-stats"><article><Layers3/><div><b>{{stats.total}} / 40</b><small>卡组总数</small></div></article><article><Sparkles/><div><b>{{stats.sites}}</b><small>场地卡 ≥ 5</small></div></article><article><Sword/><div><b>{{stats.units}}</b><small>单位卡 ≥ 15</small></div></article><article><Shield/><div><b>{{stats.avg}}</b><small>平均灵力</small></div></article></section>
    <div class="deck-layout">
      <section class="deck-list glass-panel">
        <div class="panel-title"><div><span>当前卡组</span><b>按类型与费用排列</b></div><small>点击条目预览</small></div>
        <button v-for="entry in entries" :key="entry.card.id" class="deck-row" :class="{active:selected?.id===entry.card.id}" @click="selected=entry.card"><span class="deck-glyph">{{entry.card.type==='SITE'?'◇':entry.card.type==='UNIT'?'✦':entry.card.type==='SPELL'?'⌁':'✺'}}</span><span><b>{{entry.card.name}}</b><small>{{entry.card.effect}}</small></span><em>{{entry.card.cost}} 灵力</em><strong>×{{entry.count}}</strong></button>
      </section>
      <aside class="deck-preview"><GameCard v-if="selected" :card="selected"/><div v-else class="preview-placeholder"><Layers3/><b>选择卡牌查看详情</b><p>C/R 同名最多3张，SR最多2张，SSR秘策最多1张。</p></div><div class="curve-panel glass-panel"><span>灵力曲线</span><div class="curve-bars"><i v-for="cost in [1,2,3]" :key="cost" :style="{height:`${30+deckIds.filter(id=>cards.find(c=>c.id===id)?.cost===cost).length*4}px`}"><b>{{deckIds.filter(id=>cards.find(c=>c.id===id)?.cost===cost).length}}</b><small>{{cost}}</small></i></div></div></aside>
    </div>
  </div>
</template>
