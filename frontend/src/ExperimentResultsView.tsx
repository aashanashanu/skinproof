import { useEffect, useState } from 'react'
import { ArrowLeft, CircleAlert, FlaskConical, Image as ImageIcon, Info } from 'lucide-react'
import { getConcernDescription, getScoreExplanation, type AnalysisScore } from './analysisExplanations'
import { skinConcerns } from './skinConcerns'

type ExperimentForResults = {
  id: string
  productName: string
  targetConcern: string
}

type CaptureResult = {
  captureId: string
  qualityScore: number
  baseline: boolean
  valid: boolean
  simulated: boolean
  takenAt: string
  analysisStatus: 'QUEUED' | 'PROCESSING' | 'SUCCEEDED' | 'RETRY_WAIT' | 'FAILED'
  scores: AnalysisScore[]
}

type VerdictResult = {
  verdictId: string
  concern: string
  label: 'IMPROVED' | 'NO_DETECTABLE_CHANGE' | 'WORSE' | 'INCONCLUSIVE'
  confidence: 'HIGH' | 'MEDIUM' | 'LOW'
  baselineMean: number | null
  currentMean: number | null
  delta: number | null
  noiseFloor: number
  baselineCaptureCount: number
  validCaptureCount: number
  followUpCaptureCount: number
  elapsedWeeks: number
  residualNoise: boolean
  explanation: string
  confounds: Array<{ code: string; severity: 'MINOR' | 'MAJOR' }>
}

type ExperimentResultsViewProps = {
  experiment: ExperimentForResults
  workspaceId: string
  onBack: () => void
}

const labelCopy: Record<VerdictResult['label'], string> = {
  IMPROVED: 'Improved',
  NO_DETECTABLE_CHANGE: 'No detectable change',
  WORSE: 'Worse',
  INCONCLUSIVE: 'Inconclusive',
}

function pointPath(values: Array<{ x: number; y: number }>) {
  return values.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`).join(' ')
}

export function ExperimentResultsView({ experiment, workspaceId, onBack }: ExperimentResultsViewProps) {
  const [captures, setCaptures] = useState<CaptureResult[]>([])
  const [verdict, setVerdict] = useState<VerdictResult | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  const concernMeta = skinConcerns.find((item) => item.value === experiment.targetConcern)
  const concernAction = concernMeta?.action ?? experiment.targetConcern.toLowerCase()
  const concernLabel = concernMeta?.label ?? experiment.targetConcern

  useEffect(() => {
    let isCurrent = true

    async function load() {
      try {
        const [capturesResponse, verdictResponse] = await Promise.all([
          fetch(`/api/experiments/${experiment.id}/captures`, {
            headers: { 'X-Demo-Workspace': workspaceId },
          }),
          fetch(`/api/experiments/${experiment.id}/verdict`, {
            headers: { 'X-Demo-Workspace': workspaceId },
          }),
        ])
        if (!capturesResponse.ok || !verdictResponse.ok) throw new Error('The results service is unavailable.')
        const [capturesBody, verdictBody] = await Promise.all([
          capturesResponse.json() as Promise<CaptureResult[]>,
          verdictResponse.json() as Promise<VerdictResult>,
        ])
        if (!isCurrent) return
        setCaptures(capturesBody)
        setVerdict(verdictBody)
        setError('')
      } catch {
        if (isCurrent) setError('Can’t load results right now. Check the hosted API and try again.')
      } finally {
        if (isCurrent) setIsLoading(false)
      }
    }

    void load()
    return () => {
      isCurrent = false
    }
  }, [experiment.id, workspaceId])

  const pendingCaptures = captures.filter((capture) => capture.analysisStatus === 'QUEUED' || capture.analysisStatus === 'PROCESSING' || capture.analysisStatus === 'RETRY_WAIT')
  const failedCaptures = captures.filter((capture) => capture.analysisStatus === 'FAILED')
  const orderedCaptures = [...captures].sort((first, second) => Date.parse(first.takenAt) - Date.parse(second.takenAt))
  const timelinePoints = orderedCaptures
    .filter((capture) => capture.valid && capture.analysisStatus === 'SUCCEEDED')
    .map((capture) => ({
      takenAt: capture.takenAt,
      baseline: capture.baseline,
      rawScore: capture.scores.find((score) => score.concern === concernAction)?.rawScore ?? null,
      maskUrls: capture.scores.find((score) => score.concern === concernAction)?.maskUrls ?? [],
    }))
    .filter((point) => point.rawScore !== null) as Array<{ takenAt: string; baseline: boolean; rawScore: number; maskUrls: string[] }>

  const firstBaselinePoint = timelinePoints.find((point) => point.baseline) ?? timelinePoints[0]
  const latestPoint = timelinePoints.at(-1)

  function findPreviousRawScore(captureIndex: number, concern: string) {
    for (let index = captureIndex - 1; index >= 0; index--) {
      const previousCapture = orderedCaptures[index]
      if (!previousCapture.valid || previousCapture.analysisStatus !== 'SUCCEEDED') continue
      const previousScore = previousCapture.scores.find((score) => score.concern === concern)?.rawScore
      if (previousScore !== null && previousScore !== undefined && Number.isFinite(previousScore)) {
        return previousScore
      }
    }
    return null
  }

  return (
    <main className="main-content" id="experiment-results">
      <header className="topbar">
        <span className="breadcrumb">Workspace <span>/</span> Experiments <span>/</span> {experiment.productName}</span>
      </header>

      <button className="back-link" type="button" onClick={onBack}>
        <ArrowLeft size={14} aria-hidden="true" /> Back to experiments
      </button>

      <section className="page-heading">
        <div>
          <p className="eyebrow">RESULTS</p>
          <h1>{experiment.productName}</h1>
          <p className="page-summary">Tracking {concernLabel.toLowerCase()} with raw scores, used for trend calculations.</p>
        </div>
      </section>

      {error && (
        <div className="notice notice-error" role="alert">
          <span>{error}</span>
        </div>
      )}

      {!isLoading && captures.some((capture) => capture.simulated) && (
        <div className="notice notice-info" role="status">
          <span><FlaskConical size={13} aria-hidden="true" className="notice-icon" /> This experiment uses simulated demo data for illustration. It is not a real analysis outcome.</span>
        </div>
      )}

      {isLoading ? (
        <div className="empty-state" role="status">Loading results…</div>
      ) : captures.length === 0 ? (
        <div className="empty-state">
          <div className="empty-mark" aria-hidden="true"><ImageIcon size={19} /></div>
          <h3>No captures yet</h3>
          <p>Capture a baseline photo for this experiment to start a results timeline.</p>
        </div>
      ) : (
        <>
          {verdict && (
            <section className="verdict-panel" aria-labelledby="verdict-heading">
              <div className="verdict-heading-row">
                <h2 id="verdict-heading" className={`verdict-label verdict-${verdict.label.toLowerCase()}`}>
                  {labelCopy[verdict.label]}
                </h2>
                <span className={`confidence-badge confidence-${verdict.confidence.toLowerCase()}`}>
                  {verdict.confidence.toLowerCase()} confidence
                </span>
              </div>
              <p className="verdict-explanation">{verdict.explanation}</p>
              <div className="verdict-stats">
                <span>Baseline mean {verdict.baselineMean?.toFixed(1) ?? 'n/a'}</span>
                <span>Current mean {verdict.currentMean?.toFixed(1) ?? 'n/a'}</span>
                <span>Delta {verdict.delta?.toFixed(1) ?? 'n/a'}</span>
                <span>Noise floor {verdict.noiseFloor.toFixed(1)}</span>
                <span>{verdict.validCaptureCount} valid capture{verdict.validCaptureCount === 1 ? '' : 's'}</span>
                <span>{verdict.elapsedWeeks} week{verdict.elapsedWeeks === 1 ? '' : 's'} elapsed</span>
              </div>
              {verdict.residualNoise && (
                <p className="verdict-caveat"><Info size={13} aria-hidden="true" /> Noise floor is still settling until five valid captures exist.</p>
              )}
              {verdict.confounds.length > 0 && (
                <ul className="confound-list">
                  {verdict.confounds.map((confound) => (
                    <li key={confound.code} className={`confound-${confound.severity.toLowerCase()}`}>
                      <CircleAlert size={12} aria-hidden="true" /> {confound.code.replaceAll('_', ' ').toLowerCase()}
                    </li>
                  ))}
                </ul>
              )}
              <p className="score-note"><Info size={13} aria-hidden="true" /> SkinProof tracks cosmetic trends, not medical advice.</p>
            </section>
          )}

          {pendingCaptures.length > 0 && (
            <div className="notice notice-info">
              <span>{pendingCaptures.length} capture{pendingCaptures.length === 1 ? '' : 's'} still analyzing. Refresh this view shortly.</span>
            </div>
          )}
          {failedCaptures.length > 0 && (
            <div className="notice notice-error">
              <span>{failedCaptures.length} capture{failedCaptures.length === 1 ? '' : 's'} failed analysis. Retry from the capture dialog.</span>
            </div>
          )}

          <section className="analysis-history" aria-labelledby="analysis-history-heading">
            <div className="section-heading">
              <div className="section-title-group">
                <h2 id="analysis-history-heading">Analysis details</h2>
              </div>
              <span className="section-caption">What each result means</span>
            </div>
            {orderedCaptures.map((capture, captureIndex) => (
              <details className="analysis-capture-detail" key={capture.captureId} open={captureIndex === orderedCaptures.length - 1}>
                <summary>
                  <span>{new Date(capture.takenAt).toLocaleDateString()} · {capture.baseline ? 'Baseline' : 'Follow-up'}</span>
                  <span>{capture.analysisStatus.toLowerCase().replaceAll('_', ' ')}</span>
                </summary>
                {capture.simulated && <p className="analysis-explanation">Synthetic example data; this is not a real analysis.</p>}
                {!capture.valid && <p className="analysis-explanation">This capture did not pass quality checks and is not included in the experiment trend.</p>}
                {capture.analysisStatus === 'SUCCEEDED' ? (
                  <div className="score-list analysis-history-scores">
                    {capture.scores.map((score) => {
                      const previousRawScore = capture.valid && score.rawScore !== null
                        ? findPreviousRawScore(captureIndex, score.concern)
                        : null
                      const noComparisonNote = capture.valid
                        ? 'There is no earlier valid score for this concern.'
                        : 'This capture is not used for trend comparisons because it did not pass quality checks.'
                      return (
                        <div className="analysis-score-item" key={score.concern}>
                          <div className="score-row">
                            <strong>{skinConcerns.find((item) => item.action === score.concern)?.label ?? score.concern}</strong>
                            <span>Raw {score.rawScore?.toFixed(1) ?? 'n/a'}</span>
                            <span>UI {score.uiScore ?? 'n/a'}</span>
                            <span>{score.maskUrls.length} mask{score.maskUrls.length === 1 ? '' : 's'}</span>
                          </div>
                          <p className="analysis-explanation">
                            {getConcernDescription(score.concern)}{' '}
                            {getScoreExplanation(score, previousRawScore, noComparisonNote)}
                          </p>
                        </div>
                      )
                    })}
                  </div>
                ) : (
                  <p className="analysis-explanation">No concern scores are available because this analysis has not completed successfully.</p>
                )}
              </details>
            ))}
          </section>

          <section className="timeline-section" aria-labelledby="timeline-heading">
            <div className="section-heading">
              <div className="section-title-group">
                <h2 id="timeline-heading">Timeline</h2>
              </div>
              <span className="section-caption">Raw score per valid capture</span>
            </div>
            {timelinePoints.length === 0 ? (
              <div className="empty-state">
                <p>No successfully analyzed captures for {concernLabel.toLowerCase()} yet.</p>
              </div>
            ) : (
              <Timeline points={timelinePoints} noiseFloor={verdict?.noiseFloor ?? 0} baselineMean={verdict?.baselineMean ?? null} />
            )}
          </section>

          {firstBaselinePoint && latestPoint && firstBaselinePoint !== latestPoint && (
            <section className="comparison-section" aria-labelledby="comparison-heading">
              <div className="section-heading">
                <div className="section-title-group">
                  <h2 id="comparison-heading">Before / after</h2>
                </div>
              </div>
              <div className="comparison-grid">
                <ComparisonCard label="Before" point={firstBaselinePoint} />
                <ComparisonCard label="After" point={latestPoint} />
              </div>
            </section>
          )}
        </>
      )}
    </main>
  )
}

function Timeline({
  points,
  noiseFloor,
  baselineMean,
}: {
  points: Array<{ takenAt: string; baseline: boolean; rawScore: number }>
  noiseFloor: number
  baselineMean: number | null
}) {
  const width = 640
  const height = 180
  const padding = 24
  const scores = points.map((point) => point.rawScore)
  const minScore = Math.min(...scores, baselineMean !== null ? baselineMean - noiseFloor : scores[0])
  const maxScore = Math.max(...scores, baselineMean !== null ? baselineMean + noiseFloor : scores[0])
  const range = Math.max(maxScore - minScore, 1)

  function toY(score: number) {
    return height - padding - ((score - minScore) / range) * (height - padding * 2)
  }

  function toX(index: number) {
    return points.length === 1 ? width / 2 : padding + (index / (points.length - 1)) * (width - padding * 2)
  }

  const linePoints = points.map((point, index) => ({ x: toX(index), y: toY(point.rawScore) }))
  const bandTop = baselineMean !== null ? toY(baselineMean + noiseFloor) : null
  const bandBottom = baselineMean !== null ? toY(baselineMean - noiseFloor) : null

  return (
    <svg className="timeline-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Raw score timeline">
      {bandTop !== null && bandBottom !== null && (
        <rect x={padding} y={bandTop} width={width - padding * 2} height={Math.max(bandBottom - bandTop, 1)} className="timeline-noise-band" />
      )}
      <path d={pointPath(linePoints)} className="timeline-line" fill="none" />
      {linePoints.map((point, index) => (
        <circle
          key={points[index].takenAt}
          cx={point.x}
          cy={point.y}
          r={points[index].baseline ? 5 : 3.5}
          className={points[index].baseline ? 'timeline-dot-baseline' : 'timeline-dot'}
        >
          <title>{`${new Date(points[index].takenAt).toLocaleDateString()}: ${points[index].rawScore.toFixed(1)}`}</title>
        </circle>
      ))}
    </svg>
  )
}

function ComparisonCard({ label, point }: { label: string; point: { takenAt: string; rawScore: number; maskUrls: string[] } }) {
  return (
    <div className="comparison-card">
      <strong>{label}</strong>
      <span>{new Date(point.takenAt).toLocaleDateString()}</span>
      <span className="comparison-score">Raw {point.rawScore.toFixed(1)}</span>
      {point.maskUrls[0] ? (
        <img src={point.maskUrls[0]} alt={`${label} detection mask`} className="comparison-mask" />
      ) : (
        <span className="comparison-mask-missing">No mask returned for this capture.</span>
      )}
    </div>
  )
}
