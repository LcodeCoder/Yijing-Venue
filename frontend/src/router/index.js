import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { coverGameCurtain, gameCurtain, resetGameCurtain, revealGameCurtain } from '../services/gameTransition'
import HomeView from '../views/HomeView.vue'
import BattleView from '../views/BattleView.vue'
import CollectionView from '../views/CollectionView.vue'
import DeckView from '../views/DeckView.vue'
import RankingView from '../views/RankingView.vue'
import ProfileView from '../views/ProfileView.vue'
import RulesView from '../views/RulesView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import AdminView from '../views/AdminView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/battle/:id?', name: 'battle', component: BattleView, meta: { immersive: true } },
  { path: '/collection', component: CollectionView }, { path: '/deck', component: DeckView },
  { path: '/ranking', component: RankingView }, { path: '/profile', component: ProfileView }, { path: '/rules', component: RulesView },
  { path: '/login', component: LoginView }, { path: '/register', component: RegisterView },
  { path: '/admin', component: AdminView, meta: { requiresAdmin: true } }
]
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (to, from, savedPosition) => savedPosition || ({ top: 0, behavior: 'smooth' })
})
router.beforeEach(async (to, from) => {
  if (to.name === 'battle' && to.fullPath !== from.fullPath && !gameCurtain.active) await coverGameCurtain('正在进入对局')
  const auth = useAuthStore()
  if (auth.token && !auth.user) { try { await auth.fetchMe() } catch { auth.logout() } }
  if (to.meta.requiresAdmin && !auth.isAdmin) return '/login'
})
router.afterEach(to => {
  if (to.name === 'battle' && gameCurtain.active) window.setTimeout(revealGameCurtain, 90)
})
router.onError(() => resetGameCurtain())
export default router
