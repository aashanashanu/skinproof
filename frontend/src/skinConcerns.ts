export const skinConcerns = [
  { value: 'WRINKLE', label: 'Wrinkles', action: 'wrinkle' },
  { value: 'DROOPY_UPPER_EYELID', label: 'Upper eyelid droop', action: 'droopy_upper_eyelid' },
  { value: 'DROOPY_LOWER_EYELID', label: 'Lower eyelid droop', action: 'droopy_lower_eyelid' },
  { value: 'FIRMNESS', label: 'Firmness', action: 'firmness' },
  { value: 'ACNE', label: 'Breakouts', action: 'acne' },
  { value: 'MOISTURE', label: 'Moisture', action: 'moisture' },
  { value: 'EYE_BAG', label: 'Eye bags', action: 'eye_bag' },
  { value: 'DARK_CIRCLE_V2', label: 'Dark circles', action: 'dark_circle_v2' },
  { value: 'AGE_SPOT', label: 'Dark spots', action: 'age_spot' },
  { value: 'RADIANCE', label: 'Radiance', action: 'radiance' },
  { value: 'REDNESS', label: 'Redness', action: 'redness' },
  { value: 'OILINESS', label: 'Oiliness', action: 'oiliness' },
  { value: 'PORE', label: 'Pores', action: 'pore' },
  { value: 'TEXTURE', label: 'Skin texture', action: 'texture' },
  { value: 'TEAR_TROUGH', label: 'Tear trough', action: 'tear_trough' },
  { value: 'SKIN_TYPE', label: 'Skin type', action: 'skin_type' },
] as const

export type SkinConcernCode = typeof skinConcerns[number]['value']