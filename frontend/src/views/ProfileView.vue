<script setup>
import { onMounted, ref } from 'vue'
import { Award, BookOpen, Layers3, Sparkles, Swords, Trophy } from 'lucide-vue-next'
import { api } from '../services/api'
const profile=ref({})
onMounted(async()=>profile.value=await api.profile())
</script>
<template><div class="content-page section-wrap profile-page">
<section class="profile-hero"><div class="profile-avatar">旅<span>18</span></div><div><span class="eyebrow"><Award :size="15"/>{{profile.title}}</span><h1>{{profile.name}}</h1><p>“棋盘从不沉默，它只等待正确的落子。”</p></div><div class="rank-seal"><Trophy/><b>{{profile.rank}}</b><small>{{profile.rating}} 赛季积分</small></div></section>
<section class="profile-stat-grid"><article><Swords/><b>{{profile.wins}}</b><span>胜利对局</span></article><article><Sparkles/><b>{{profile.games}}</b><span>总对局</span></article><article><BookOpen/><b>{{profile.collection}} / {{profile.collectionTotal}}</b><span>藏品收集</span></article><article><Layers3/><b>3</b><span>有效卡组</span></article></section>
<div class="profile-columns"><section class="glass-panel profile-panel"><div class="panel-title"><div><span>近期战绩</span><b>最近五场对局</b></div></div><div class="match-history"><article class="win"><i>胜</i><span><b>场地绝杀</b><small>五域初阵 vs 赤焰速攻</small></span><em>3 : 1</em></article><article class="win"><i>胜</i><span><b>积分结算</b><small>镜潮控场 vs 玄岩壁垒</small></span><em>27 : 21</em></article><article class="loss"><i>负</i><span><b>场地绝杀</b><small>星图回响 vs 裂隙奔袭</small></span><em>2 : 3</em></article></div></section><section class="glass-panel profile-panel"><div class="panel-title"><div><span>赛季专精</span><b>{{profile.season}}</b></div></div><div class="mastery"><span class="mastery-ring">63<small>%</small></span><div><b>镜潮控场</b><p>最常使用卡组</p><div class="progress"><i style="width:63%"></i></div><small>再赢 7 场提升专精等级</small></div></div></section></div>
</div></template>
