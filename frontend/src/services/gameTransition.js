import { reactive } from 'vue'

export const gameCurtain = reactive({
  active: false,
  phase: 'idle',
  label: '正在开启弈境'
})

let phaseTimer
let closePromise

const reducedMotion = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

export async function coverGameCurtain(label = '正在开启弈境') {
  gameCurtain.label = label
  clearTimeout(phaseTimer)
  if (gameCurtain.active && ['closing', 'sealed'].includes(gameCurtain.phase)) return closePromise || Promise.resolve()
  gameCurtain.active = true
  gameCurtain.phase = reducedMotion() ? 'sealed' : 'closing'
  closePromise = reducedMotion() ? Promise.resolve() : wait(620)
  await closePromise
  if (gameCurtain.active) gameCurtain.phase = 'sealed'
}

export function revealGameCurtain() {
  clearTimeout(phaseTimer)
  if (!gameCurtain.active) return
  requestAnimationFrame(() => requestAnimationFrame(() => {
    gameCurtain.phase = reducedMotion() ? 'idle' : 'opening'
    phaseTimer = setTimeout(() => {
      gameCurtain.active = false
      gameCurtain.phase = 'idle'
      closePromise = null
    }, reducedMotion() ? 20 : 760)
  }))
}

export function resetGameCurtain() {
  clearTimeout(phaseTimer)
  gameCurtain.active = false
  gameCurtain.phase = 'idle'
  closePromise = null
}
