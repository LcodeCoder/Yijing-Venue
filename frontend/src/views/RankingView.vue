<script setup>
import { onMounted, ref } from 'vue'
import { Trophy, TrendingUp, Crown } from 'lucide-vue-next'
import { api } from '../services/api'
const rows=ref([])
onMounted(async()=>rows.value=await api.rankings())
</script>
<template><div class="content-page section-wrap">
<header class="page-heading"><span>雾海纪元 · 赛季天梯</span><h1>执棋者荣誉榜</h1><p>天梯按赛季积分排名；同分时，场地绝杀率更高者优先。</p></header>
<section class="podium" v-if="rows.length"><article v-for="row in rows.slice(0,3)" :key="row.rank" :class="`place-${row.rank}`"><span class="podium-rank"><Crown v-if="row.rank===1"/>{{row.rank}}</span><span class="large-avatar">{{row.avatar}}</span><h3>{{row.name}}</h3><p>{{row.tier}}</p><b>{{row.rating}}</b><small>赛季积分</small></article></section>
<section class="ranking-table glass-panel"><header><span>排名</span><span>执棋者</span><span>段位</span><span>胜率</span><span>积分</span></header><article v-for="row in rows" :key="row.rank" :class="{'is-me':row.name==='弈境旅者'}"><b>#{{row.rank}}</b><span class="rank-player"><i>{{row.avatar}}</i><strong>{{row.name}}<small v-if="row.name==='弈境旅者'">你</small></strong></span><span>{{row.tier}}</span><span><TrendingUp :size="15"/>{{row.winRate}}%</span><strong>{{row.rating}}</strong></article></section>
</div></template>
