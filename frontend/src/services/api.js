const json = async (url, options = {}) => {
  const token = localStorage.getItem('fieldrealm-token')
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    }
  })
  if (response.status === 204) return null
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || '弈境连接中断，请稍后重试')
  return body
}

export const api = {
  cards: () => json('/api/cards'),
  cardTags: () => json('/api/cards/tags'),
  profile: () => json('/api/profile'),
  rankings: () => json('/api/rankings'),
  starterDeck: (archetype = 'balanced') => json(`/api/decks/starter?archetype=${encodeURIComponent(archetype)}`),
  archetypes: () => json('/api/decks/archetypes'),
  deckRules: () => json('/api/decks/rules'),
  puzzles: () => json('/api/puzzles'),
  login: payload => json('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  emailStatus: () => json('/api/auth/email-status'),
  sendEmailCode: (email, purpose) => json('/api/auth/email-code', { method: 'POST', body: JSON.stringify({ email, purpose }) }),
  emailLogin: payload => json('/api/auth/login/email', { method: 'POST', body: JSON.stringify(payload) }),
  register: payload => json('/api/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  me: () => json('/api/auth/me'),
  updateProfile: payload => json('/api/auth/me', { method: 'PUT', body: JSON.stringify(payload) }),
  createMatch: (playerName = '', options = {}) => json('/api/matches', {
    method: 'POST',
    body: JSON.stringify({
      mode: options.mode || 'AI',
      playerName,
      boardSize: options.boardSize || 3,
      ranked: Boolean(options.ranked),
      aiDifficulty: options.aiDifficulty || 'normal',
      scenario: options.scenario || 'standard',
      deckArchetype: options.deckArchetype || 'balanced',
      puzzleId: options.puzzleId || null
    })
  }),
  joinMatch: (id, playerName = '') => json(`/api/matches/${id}/join`, { method: 'POST', body: JSON.stringify({ playerName }) }),
  match: id => json(`/api/matches/${id}`),
  matchCooldown: () => json('/api/matches/cooldown'),
  leaveMatch: (id, playerId) => json(`/api/matches/${id}/leave`, { method: 'POST', body: JSON.stringify({ playerId }) }),
  queueRanked: boardSize => json('/api/matchmaking/queue', { method: 'POST', body: JSON.stringify({ boardSize }) }),
  queueStatus: () => json('/api/matchmaking/status'),
  cancelQueue: () => json('/api/matchmaking/queue', { method: 'DELETE' }),
  adminCards: () => json('/api/admin/cards'),
  adminUsers: () => json('/api/admin/users'),
  adminMail: () => json('/api/admin/mail'),
  saveAdminMail: payload => json('/api/admin/mail', { method: 'PUT', body: JSON.stringify(payload) }),
  testAdminMail: email => json('/api/admin/mail/test', { method: 'POST', body: JSON.stringify({ email }) }),
  createCard: card => json('/api/admin/cards', { method: 'POST', body: JSON.stringify(card) }),
  updateCard: (id, card) => json(`/api/admin/cards/${id}`, { method: 'PUT', body: JSON.stringify(card) }),
  deleteCard: id => json(`/api/admin/cards/${id}`, { method: 'DELETE' }),
  playCard: (id, payload) => json(`/api/matches/${id}/cards`, { method: 'POST', body: JSON.stringify(payload) }),
  contest: (id, playerId) => json(`/api/matches/${id}/contest`, { method: 'POST', body: JSON.stringify({ playerId }) }),
  attack: (id, payload) => json(`/api/matches/${id}/attacks`, { method: 'POST', body: JSON.stringify(payload) }),
  endTurn: (id, playerId) => json(`/api/matches/${id}/end-turn`, { method: 'POST', body: JSON.stringify({ playerId }) }),
  retreatUnit: (id, playerId, unitId) => json(`/api/matches/${id}/units/retreat`, { method: 'POST', body: JSON.stringify({ playerId, unitId }) }),
  moveUnit: (id, playerId, unitId, targetSiteIndex) => json(`/api/matches/${id}/units/move`, {
    method: 'POST',
    body: JSON.stringify({ playerId, unitId, targetSiteIndex })
  }),
  cycleCard: (id, playerId, cardId) => json(`/api/matches/${id}/cycle`, { method: 'POST', body: JSON.stringify({ playerId, cardId }) }),
  discard: (id, playerId, cardId) => json(`/api/matches/${id}/discard`, { method: 'POST', body: JSON.stringify({ playerId, cardId }) })
}
