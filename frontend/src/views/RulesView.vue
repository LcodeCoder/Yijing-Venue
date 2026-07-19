<script setup>
import { BookOpen, CircleDot, Coins, Flag, Layers3, RefreshCcw, Shield, Sparkles, Swords } from 'lucide-vue-next'
const steps = [
  { icon: BookOpen, title: '抽牌阶段', text: '每回合自动抽2张。手牌超过7张时仍可继续行动，回合结束时系统自动弃至7张。' },
  { icon: Sparkles, title: '资源阶段', text: '灵力清空并按棋盘边长刷新：3×3为3点，4×4为4点，5×5为5点，不会跨回合累计。' },
  { icon: Layers3, title: '部署阶段', text: '在60秒内部署或覆盖场地，并在己方场地驻扎单位。单个场地最多2个单位。' },
  { icon: Swords, title: '争夺阶段', text: '在60秒内选择未行动单位攻击射程内的敌方场地，战力大于总守力即可夺取。' },
  { icon: Coins, title: '结算阶段', text: '所有有归属的场地提供积分，两个核心场地积分权重×2。' }
]
</script>
<template>
  <div class="content-page section-wrap rules-page">
    <header class="page-heading"><span>官方规则手册</span><h1>九域相连，用距离重写主动权</h1><p>《场地弈境》没有生命值与单位阵亡。你要管理有限灵力、两个驻场位、攻击距离与不断变化的场地归属。</p></header>
    <section class="rule-callout"><Flag/><div><b>核心目标</b><p>控制过半场地并连续保持两次结算，可触发「场地绝杀」；否则在3×3的9回合、4×4的12回合或5×5的15回合后，以更高累计积分取胜。</p></div></section>
    <section class="rules-grid">
      <article><span><CircleDot/></span><h3>可变棋盘场地</h3><p>可选3×3、4×4或5×5域面。棋盘越大，每回合灵力与总回合数也随之提升。</p></article>
      <article><span><Sparkles/></span><h3>棋盘灵力</h3><p>每回合灵力等于棋盘边长：3×3为3点、4×4为4点、5×5为5点，不积累。</p></article>
      <article><span><Shield/></span><h3>两个驻场位</h3><p>每个场地最多2个单位。部署阶段可将己方单位撤离至弃牌堆，不返还灵力或手牌。</p></article>
      <article><span><RefreshCcw/></span><h3>攻击距离</h3><p>普通单位射程为1，只能争夺环形相邻场地。踏界斥候基础射程2，「折界通路」可使己方单位本回合射程+1。</p></article>
    </section>
    <section class="turn-flow"><div class="section-heading"><span>标准回合</span><h2>五步完成一次局势闭环</h2></div><ol><li v-for="(step,i) in steps" :key="step.title"><span>{{i+1}}</span><component :is="step.icon"/><div><b>{{step.title}}</b><p>{{step.text}}</p></div></li></ol></section>
    <section class="card-types-rule glass-panel"><h2>四类卡牌</h2><div><article><i>◇</i><b>场地卡</b><p>建立归属、提供守力与常驻积分。</p></article><article><i>✦</i><b>单位卡</b><p>以战力发起射程内争夺，以守力巩固归属。</p></article><article><i>⌁</i><b>瞬发术式</b><p>抽牌、封印、增幅或扩展攻击距离。</p></article><article><i>✺</i><b>局势秘策</b><p>满足条件后发动，每局最多一次。</p></article></div></section>
  </div>
</template>
