import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '../services/api'

const TOKEN_KEY = 'fieldrealm-token'
const USER_KEY = 'fieldrealm-user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))
  const isLoggedIn = computed(() => Boolean(token.value && user.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  function persist(session) {
    token.value = session.token; user.value = session.user
    localStorage.setItem(TOKEN_KEY, token.value); localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }
  async function login(payload) { const session = await api.login(payload); persist(session); return session }
  async function emailLogin(payload) { const session = await api.emailLogin(payload); persist(session); return session }
  async function register(payload) { const session = await api.register(payload); persist(session); return session }
  async function fetchMe() { if (!token.value) return null; user.value = await api.me(); localStorage.setItem(USER_KEY, JSON.stringify(user.value)); return user.value }
  function logout() { token.value = ''; user.value = null; localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY) }
  return { token, user, isLoggedIn, isAdmin, login, emailLogin, register, fetchMe, logout }
})
