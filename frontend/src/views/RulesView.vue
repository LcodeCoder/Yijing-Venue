<script setup>
import { BookOpen, CircleDot, Coins, Flag, Layers3, RefreshCcw, Shield, Sparkles, Swords, Move, Target } from 'lucide-vue-next'
const steps = [
  { icon: BookOpen, title: '抽牌阶段', text: '每回合自动抽2张。手牌超过7张时仍可继续行动，回合结束时系统自动弃至7张。' },
  { icon: Sparkles, title: '资源阶段', text: '灵力清空并按棋盘边长刷新：3×3为3点，4×4为4点，5×5为5点，不会跨回合累计。' },
  { icon: Layers3, title: '部署阶段', text: '部署/覆盖场地、驻扎单位、调防（1灵力移至相邻己方场）、筛牌（弃1抽1，每回合1次）。人机90秒/排位60秒。' },
  { icon: Swords, title: '争夺阶段', text: '选择未行动单位攻击射程内的敌方场地。夹击（≥2相邻己方场）战力+1。战力大于总守力即可夺取。' },
  { icon: Coins, title: '结算阶段', text: '有归属场地提供积分（邻接协同额外+1，核心×2）。控制过半场地则绝杀进度+1，否则重置。' }
]
</script>
<template>
  <div class="content-page section-wrap rules-page">
    <header class="page-heading">
      <span>官方规则手册</span>
      <h1>九域相连，用距离重写主动权</h1>
      <p>《场地弈境》没有生命值与单位阵亡。你要管理有限灵力、两个驻场位、攻击距离、邻接连片与不断变化的场地归属。绝杀进度打断即重置。</p>
    </header>
    <section class="rule-callout">
      <Flag/>
      <div>
        <b>核心目标</b>
        <p>控制过半场地并连续保持两次结算，可触发「场地绝杀」（进度1/2可见于对局HUD）；否则在3×3的9回合、4×4的12回合或5×5的15回合后，以更高累计积分取胜。终局回合不可新部署场地。</p>
      </div>
    </section>
    <section class="rules-grid">
      <article><span><CircleDot/></span><h3>可变棋盘场地</h3><p>可选3×3、4×4或5×5域面。四角为边陲：部署费-1，但该场积分计为0。棋盘越大，每回合灵力与总回合数也随之提升。</p></article>
      <article><span><Sparkles/></span><h3>棋盘灵力与气势</h3><p>每回合灵力等于棋盘边长，不积累。争夺成功气势+1、失败-1；气势满3层可免费发动1费术式。</p></article>
      <article><span><Shield/></span><h3>驻场与状态</h3><p>每场最多2单位。单位可撤离或花费1灵力调防至相邻己方场。动摇（被夺场）不可主动攻；扎根（连驻2回合）守力+1。</p></article>
      <article><span><RefreshCcw/></span><h3>攻击距离与夹击</h3><p>普通单位射程1。斥候2、夜航游侠3。目标被≥2块己方邻接场夹住时战力+1。邻接己方场守力+1并结算+1分。</p></article>
      <article><span><Move/></span><h3>调防与覆盖</h3><p>部署阶段可将单位调防到相邻己方空位。对己方场再打场地卡可覆盖改造效果，单位保留。</p></article>
      <article><span><Target/></span><h3>筛牌与弃牌收益</h3><p>每回合可筛牌一次（弃1抽1）。主动弃场地额外抽1；弃单位使一名单位本回合射程+1。</p></article>
    </section>
    <section class="turn-flow">
      <div class="section-heading"><span>标准回合</span><h2>五步完成一次局势闭环</h2></div>
      <ol>
        <li v-for="(step,i) in steps" :key="step.title">
          <span>{{ i + 1 }}</span>
          <component :is="step.icon"/>
          <div><b>{{ step.title }}</b><p>{{ step.text }}</p></div>
        </li>
      </ol>
    </section>
    <section class="card-types-rule glass-panel">
      <h2>四类卡牌</h2>
      <div>
        <article><i>◇</i><b>场地卡</b><p>建立归属、提供守力与常驻积分，可覆盖改造。</p></article>
        <article><i>✦</i><b>单位卡</b><p>以战力发起射程内争夺，以守力巩固归属。</p></article>
        <article><i>⌁</i><b>瞬发术式</b><p>抽牌、封印、增幅或扩展攻击距离。</p></article>
        <article><i>✺</i><b>局势秘策</b><p>满足条件后发动，每局最多一次。</p></article>
      </div>
    </section>
  </div>
</template>
