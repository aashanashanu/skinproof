export type AnalysisScore = {
  concern: string
  rawScore: number | null
  uiScore: number | null
  maskUrls: string[]
}

const concernDescriptions: Record<string, string> = {
  wrinkle: 'Looks at the appearance of wrinkles.',
  droopy_upper_eyelid: 'Looks at the appearance of upper eyelid droop.',
  droopy_lower_eyelid: 'Looks at the appearance of lower eyelid droop.',
  firmness: 'Looks at the appearance of skin firmness.',
  acne: 'Looks at the appearance of breakouts.',
  moisture: 'Looks at the appearance of skin moisture.',
  eye_bag: 'Looks at the appearance of under-eye bags.',
  dark_circle_v2: 'Looks at the appearance of under-eye dark circles.',
  age_spot: 'Looks at the appearance of dark spots.',
  radiance: 'Looks at the appearance of skin brightness.',
  redness: 'Looks at the appearance of redness.',
  oiliness: 'Looks at the appearance of skin oiliness.',
  pore: 'Looks at the appearance of visible pores.',
  texture: 'Looks at the appearance of skin texture.',
  tear_trough: 'Looks at the appearance of tear troughs.',
  skin_type: 'Returns a skin-type category rather than a numeric score.',
  all: 'Summarizes the overall cosmetic skin score.',
  skin_age: 'Returns a provider-estimated skin age, not a concern score.',
  resize_image: 'Prepares the image for analysis; this is not a skin concern score.',
}

export function getConcernDescription(concern: string) {
  return concernDescriptions[concern] ?? 'This is a provider analysis result.'
}

export function getScoreExplanation(
  score: AnalysisScore,
  previousRawScore?: number | null,
  noComparisonNote = 'There is no earlier valid score for comparison.',
) {
  if (score.concern === 'skin_type') {
    return 'This result is categorical, so it is not interpreted as a numeric trend. SkinProof does not infer a diagnosis from it.'
  }

  if (score.concern === 'skin_age') {
    return 'This is a provider estimate, not a measure of skin health, and is not used in the concern trend.'
  }

  if (score.concern === 'resize_image') {
    return 'This is an image-preparation output, not a concern score.'
  }

  if (score.rawScore === null || !Number.isFinite(score.rawScore)) {
    return score.maskUrls.length > 0
      ? 'A mask was returned, but no numeric raw score was provided for this result.'
      : 'No numeric raw score or detection mask was returned for this result.'
  }

  const scoreContext = `Raw score ${score.rawScore.toFixed(1)} out of 100. YouCam describes higher raw scores as more favorable. This is a cosmetic indicator, not a severity rating.`
  const uiContext = score.uiScore === null
    ? ''
    : ` UI score ${score.uiScore} is favorably adjusted and is not used for trend calculations.`

  if (previousRawScore === undefined || previousRawScore === null || !Number.isFinite(previousRawScore)) {
    return `${scoreContext}${uiContext} ${noComparisonNote}`
  }

  const delta = score.rawScore - previousRawScore
  const movement = Math.abs(delta) < 0.05
    ? 'is unchanged'
    : delta > 0
      ? `is up ${delta.toFixed(1)} points`
      : `is down ${Math.abs(delta).toFixed(1)} points`
  return `${scoreContext}${uiContext} Compared with the previous valid analysis for this concern, the raw score ${movement}. The comparison does not show that a product caused the difference.`
}