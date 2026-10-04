import { useEffect, useState } from 'react'
import { Check, CircleAlert, Info, RefreshCw, Upload, X } from 'lucide-react'
import { CameraKitCapture, type CaptureQuality } from './CameraKitCapture'
import { getConcernDescription, getScoreExplanation } from './analysisExplanations'
import { skinConcerns, type SkinConcernCode } from './skinConcerns'

type ExperimentForCapture = {
  id: string
  productName: string
  targetConcern: string
}

type CaptureResult = {
  captureId: string
  qualityScore: number
  qualityFlags: string[]
  baseline: boolean
  valid: boolean
  takenAt: string
  analysisStatus: 'QUEUED' | 'PROCESSING' | 'SUCCEEDED' | 'RETRY_WAIT' | 'FAILED'
  providerTaskId: string | null
  scores: Array<{
    concern: string
    rawScore: number | null
    uiScore: number | null
    maskUrls: string[]
  }>
  errorCode: string | null
  errorMessage: string | null
  attemptCount: number
}

type ExperimentCaptureDialogProps = {
  experiment: ExperimentForCapture
  workspaceId: string
  onClose: () => void
}

const activeStatuses = new Set<CaptureResult['analysisStatus']>(['QUEUED', 'PROCESSING', 'RETRY_WAIT'])

function delay(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(resolve, milliseconds)
    signal.addEventListener('abort', () => {
      window.clearTimeout(timer)
      reject(new DOMException('Polling aborted', 'AbortError'))
    }, { once: true })
  })
}

export function ExperimentCaptureDialog({ experiment, workspaceId, onClose }: ExperimentCaptureDialogProps) {
  const initialTarget = skinConcerns.find((concern) => concern.value === experiment.targetConcern)?.value ?? 'ACNE'
  const [consented, setConsented] = useState(false)
  const [image, setImage] = useState<File | null>(null)
  const [captureQuality, setCaptureQuality] = useState<CaptureQuality | undefined>()
  const [concern, setConcern] = useState<SkinConcernCode>(initialTarget)
  const [baseline, setBaseline] = useState(false)
  const [capture, setCapture] = useState<CaptureResult | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState('')
  const captureId = capture?.captureId
  const analysisStatus = capture?.analysisStatus

  useEffect(() => {
    if (!captureId || !analysisStatus || !activeStatuses.has(analysisStatus)) return
    const controller = new AbortController()
    const currentCaptureId = captureId

    async function poll() {
      while (!controller.signal.aborted) {
        try {
          await delay(2500, controller.signal)
          const response = await fetch(`/api/captures/${currentCaptureId}`, {
            headers: { 'X-Demo-Workspace': workspaceId },
            signal: controller.signal,
          })
          if (!response.ok) throw new Error('Unable to refresh analysis status.')
          const updated = await response.json() as CaptureResult
          setCapture(updated)
          if (!activeStatuses.has(updated.analysisStatus)) return
        } catch (pollError) {
          if (pollError instanceof DOMException && pollError.name === 'AbortError') return
          setError('Analysis status could not be refreshed. The capture remains saved; try refreshing this view.')
          return
        }
      }
    }

    void poll()
    return () => controller.abort()
  }, [captureId, analysisStatus, workspaceId])

  async function submitCapture() {
    if (!image || !consented || isSubmitting) return
    setIsSubmitting(true)
    setError('')

    const body = new FormData()
    body.set('image', image)
    if (captureQuality) {
      body.set('quality', new Blob([JSON.stringify(captureQuality)], { type: 'application/json' }), 'quality.json')
    }
    const query = new URLSearchParams({ concerns: concern, baseline: String(baseline) })

    try {
      const response = await fetch(`/api/experiments/${experiment.id}/captures?${query}`, {
        method: 'POST',
        headers: {
          'X-Demo-Workspace': workspaceId,
          'X-SkinProof-Consent': 'true',
        },
        body,
      })
      const responseBody = await response.json() as CaptureResult & { message?: string; detail?: string }
      if (!response.ok) throw new Error(responseBody.detail ?? responseBody.message ?? 'Capture could not be saved.')
      setCapture(responseBody)
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Capture could not be saved. Try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function retryAnalysis() {
    if (!capture) return
    setError('')
    try {
      const response = await fetch(`/api/captures/${capture.captureId}/retry`, {
        method: 'POST',
        headers: { 'X-Demo-Workspace': workspaceId },
      })
      const responseBody = await response.json() as CaptureResult & { message?: string; detail?: string }
      if (!response.ok) throw new Error(responseBody.detail ?? responseBody.message ?? 'Retry could not be queued.')
      setCapture(responseBody)
    } catch (retryError) {
      setError(retryError instanceof Error ? retryError.message : 'Retry could not be queued.')
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !isSubmitting) onClose()
    }}>
      <section className="dialog capture-dialog" role="dialog" aria-modal="true" aria-labelledby="capture-dialog-title">
        <div className="dialog-heading">
          <div>
            <p className="eyebrow">CAPTURE</p>
            <h2 id="capture-dialog-title">{experiment.productName}</h2>
          </div>
          <button className="icon-button close-button" type="button" aria-label="Close capture dialog" onClick={onClose}><X size={17} /></button>
        </div>
        <p className="dialog-intro">Capture the same way each time. SkinProof keeps the image private in AWS and sends it to YouCam only after consent.</p>

        {!capture && (
          <>
            <label className="consent-row">
              <input type="checkbox" checked={consented} onChange={(event) => setConsented(event.target.checked)} />
              <span>I consent to storing this image privately and sending it to YouCam for cosmetic analysis.</span>
            </label>
            {consented && (
              <>
                <CameraKitCapture onCaptured={(capturedImage, quality) => {
                  setImage(capturedImage)
                  setCaptureQuality(quality)
                }} />
                <label className="upload-control">
                  <span><Upload size={15} aria-hidden="true" /> Upload photo fallback</span>
                  <input type="file" accept="image/jpeg,image/png" onChange={(event) => {
                    setImage(event.target.files?.[0] ?? null)
                    setCaptureQuality(undefined)
                  }} />
                </label>
                <label className="form-label">
                  Target concern
                  <select value={concern} onChange={(event) => setConcern(event.target.value as SkinConcernCode)}>
                    {skinConcerns.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
                  </select>
                </label>
                <label className="baseline-row">
                  <input type="checkbox" checked={baseline} onChange={(event) => setBaseline(event.target.checked)} />
                  Mark as baseline capture
                </label>
                {image && <p className="capture-file">{image.name} · {Math.ceil(image.size / 1024)} KB</p>}
                <div className="dialog-actions">
                  <button className="primary-button" type="button" disabled={!image || isSubmitting} onClick={() => void submitCapture()}>
                    {isSubmitting ? 'Saving and queueing…' : 'Save capture and analyze'}
                  </button>
                </div>
              </>
            )}
          </>
        )}

        {capture && (
          <section className="capture-status-panel" aria-live="polite">
            <div className={`capture-status-indicator status-${capture.analysisStatus.toLowerCase()}`}>
              {capture.analysisStatus === 'SUCCEEDED' ? <Check size={15} /> : <RefreshCw size={15} className={activeStatuses.has(capture.analysisStatus) ? 'spinner' : ''} />}
              <strong>{capture.analysisStatus === 'SUCCEEDED' ? 'Analysis complete' : capture.analysisStatus.replace('_', ' ')}</strong>
              <span>Attempt {capture.attemptCount}</span>
            </div>
            <div className="quality-summary">
              <strong>Capture quality: {capture.qualityScore}/100</strong>
              <span>{capture.valid ? 'Camera quality passed' : 'Manual upload; face position and pose are unverified'}</span>
              {capture.qualityFlags.map((flag) => <span key={flag}>{flag.replaceAll('_', ' ').toLowerCase()}</span>)}
            </div>
            {capture.errorMessage && <div className="notice notice-error"><CircleAlert size={14} /> {capture.errorMessage}</div>}
            {capture.analysisStatus === 'FAILED' && <button className="secondary-button" type="button" onClick={() => void retryAnalysis()}>Retry analysis</button>}
            {capture.analysisStatus === 'SUCCEEDED' && (
              <div className="score-list">
                {capture.scores.map((score) => (
                  <div className="analysis-score-item" key={score.concern}>
                    <div className="score-row">
                      <strong>{skinConcerns.find((item) => item.action === score.concern)?.label ?? score.concern}</strong>
                      <span>Raw {score.rawScore?.toFixed(1) ?? 'n/a'}</span>
                      <span>UI {score.uiScore ?? 'n/a'}</span>
                      <span>{score.maskUrls.length} mask{score.maskUrls.length === 1 ? '' : 's'}</span>
                    </div>
                    <p className="analysis-explanation">
                      {getConcernDescription(score.concern)} {getScoreExplanation(score)}
                    </p>
                  </div>
                ))}
                <p className="score-note"><Info size={13} /> Raw scores are used for trend calculations.</p>
              </div>
            )}
            <div className="dialog-actions">
              <button className="secondary-button" type="button" onClick={onClose}>Done</button>
            </div>
          </section>
        )}

        {error && <div className="notice notice-error" role="alert"><CircleAlert size={14} /> {error}</div>}
      </section>
    </div>
  )
}