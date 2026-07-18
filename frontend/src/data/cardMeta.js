export const typeMeta = {
  SITE: { label: '场地', glyph: '◇', color: '#64d4a7' },
  UNIT: { label: '单位', glyph: '✦', color: '#f0c778' },
  SPELL: { label: '术式', glyph: '⌁', color: '#8cb8ff' },
  SECRET: { label: '秘策', glyph: '✺', color: '#d59cff' }
}

export const rarityName = { C: '普通', R: '稀有', SR: '超稀有', SSR: '极致秘策' }
export const rarityClass = rarity => `rarity-${String(rarity || 'C').toLowerCase()}`
