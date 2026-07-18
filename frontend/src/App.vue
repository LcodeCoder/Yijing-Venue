<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Home, Layers3, Library, Trophy, UserRound, BookOpen, Menu, X, Shield, LogIn, LogOut } from 'lucide-vue-next'
import { useAuthStore } from './stores/auth'
import { gameCurtain } from './services/gameTransition'
const route = useRoute(), auth = useAuthStore(), open = ref(false)
const immersive = computed(() => route.meta.immersive)
watch(() => route.fullPath, () => { open.value = false })
const links = [
  { to: '/', label: '弈境大厅', icon: Home }, { to: '/deck', label: '卡组构筑', icon: Layers3 },
  { to: '/collection', label: '藏品图鉴', icon: Library }, { to: '/ranking', label: '赛季天梯', icon: Trophy },
  { to: '/rules', label: '规则手册', icon: BookOpen }, { to: '/profile', label: '执棋档案', icon: UserRound }
]
</script>
<template>
  <div class="app-shell" :class="{ immersive }">
    <header v-if="!immersive" class="topbar">
      <router-link to="/" class="brand"><span class="brand-mark"><i></i><b></b></span><span><strong>场地弈境</strong><small>FIELD REALM</small></span></router-link>
      <nav class="desktop-nav"><router-link v-for="item in links" :key="item.to" :to="item.to"><component :is="item.icon" :size="17"/>{{ item.label }}</router-link></nav>
      <div class="auth-nav">
        <router-link v-if="!auth.isLoggedIn" to="/login"><LogIn :size="16"/>登录</router-link>
        <template v-else><span class="user-pill">{{ auth.user?.displayName || auth.user?.name }}</span><router-link v-if="auth.isAdmin" to="/admin"><Shield :size="16"/>管理</router-link><button class="text-button" @click="auth.logout"><LogOut :size="16"/>退出</button></template>
      </div>
      <button class="icon-button mobile-menu" @click="open=!open"><X v-if="open"/><Menu v-else/></button>
    </header>
    <transition name="slide-down"><nav v-if="open && !immersive" class="mobile-nav"><router-link v-for="item in links" :key="item.to" :to="item.to" @click="open=false"><component :is="item.icon" :size="19"/>{{ item.label }}</router-link><router-link v-if="auth.isAdmin" to="/admin">管理后台</router-link><router-link v-if="!auth.isLoggedIn" to="/login">登录</router-link></nav></transition>
    <main :class="{ 'page-main': !immersive }">
      <router-view v-slot="{ Component, route: viewRoute }">
        <transition name="route-view" mode="out-in">
          <component :is="Component" :key="viewRoute.fullPath" />
        </transition>
      </router-view>
    </main>
    <footer v-if="!immersive" class="footer"><div class="brand footer-brand"><span class="brand-mark"><i></i><b></b></span><span><strong>场地弈境</strong><small>掌控场地，重写局势</small></span></div><div class="footer-meta"><p>原创无血量控场卡牌游戏 · 当前版本「雾海纪元」</p><small>海宇信息科技有限公司</small></div></footer>
    <div v-if="gameCurtain.active" class="game-curtain" :class="`curtain-${gameCurtain.phase}`" role="status" aria-live="polite">
      <div class="curtain-panel curtain-left"><span></span><i></i></div>
      <div class="curtain-panel curtain-right"><span></span><i></i></div>
      <div class="curtain-seal">
        <span class="brand-mark"><i></i><b></b></span>
        <small>FIELD REALM</small>
        <strong>{{ gameCurtain.label }}</strong>
        <em>弈境正在展开</em>
      </div>
    </div>
  </div>
</template>
