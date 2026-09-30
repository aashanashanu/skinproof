export const skinConcerns = [
  { value: 'ACNE', label: 'Breakouts', action: 'acne' },
  { value: 'AGE_SPOT', label: 'Dark spots', action: 'age_spot' },
  { value: 'REDNESS', label: 'Redness', action: 'redness' },
  { value: 'TEXTURE', label: 'Skin texture', action: 'texture' },
] as const

export type SkinConcernCode = typeof skinConcerns[number]['value']