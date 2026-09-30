import { useState } from 'react'
import { Check, CircleAlert, Info, Upload, X } from 'lucide-react'
import { CameraKitCapture } from './CameraKitCapture'
import { skinConcerns, type SkinConcernCode } from './skinConcerns'

type AnalysisScore = {
  concern: string
  rawScore: number | null
  uiScore: number | null
  maskUrls: string[]
}

type AnalysisResponse = {
  taskId: string
  mode: string
  documentedUnitEstimate: number
  scores: AnalysisScore[]
}

type CameraSpikeViewProps = {
  onClose: () => void
}

export function CameraSpikeView({ onClose }: CameraSpikeViewProps) {
  const [hasConsent, setHasConsent] = useState(false)
  const [image, setImage] = useState<File | null>(null)
  const [selectedConcerns, setSelectedConcerns] = useState<SkinConcernCode[]>(['ACNE', 'TEXTURE'])
  const [result, setResult] = useState<AnalysisResponse | null>(null)
  const [error, setError] = useState('')
  const [isAnalyzing, setIsAnalyzing] = useState(false)

  function updateConcern(concern: SkinConcernCode, selected: boolean) {
    setSelectedConcerns((current) => {
      if (selected) return current.length < skinConcerns.length && !current.includes(concern) ? [...current, concern] : current
      return current.filter((value) => value !== concern)
    })
    setResult(null)
  }

  async function analyzeImage() {
    if (!image || selectedConcerns.length === 0 || !hasConsent) return

    setIsAnalyzing(true)
    setError('')
    setResult(null)

    const form = new FormData()
    form.set('image', image)
    const query = selectedConcerns.map((concern) => `concerns=${encodeURIComponent(concern)}`).join('&')

    try {
      const response = await fetch(`/api/analysis/skin?${query}`, {
        method: 'POST',
        headers: { 'X-SkinProof-Consent': 'true' },
        body: form,
      })
      const responseBody = await response.json() as AnalysisResponse & { message?: string; detail?: string }
      if (!response.ok) throw new Error(responseBody.detail ?? responseBody.message ?? 'Skin analysis failed.')
      setResult(responseBody)
    } catch (analysisError) {
      setError(analysisError instanceof Error ? analysisError.message : 'Skin analysis failed. Try again.')
    } finally {
      setIsAnalyzing(false)
    }
  }

  return (
    <section className="camera-spike" aria-labelledby="camera-spike-title">
      <div className="dialog-heading">
        <div>
          <p className="eyebrow">M0 CAMERA + API SPIKE</p>
          <h2 id="camera-spike-title">Check capture quality</h2>
        </div>
        <button className="icon-button close-button" type="button" aria-label="Close camera test" onClick={onClose}>
          <X size={17} />
        </button>
      </div>
      <p className="dialog-intro">Use guided capture or upload an approved sample photo. The image is sent to your configured YouCam API for analysis and is not saved by SkinProof.</p>

      <label className="consent-row">
        <input type="checkbox" checked={hasConsent} onChange={(event) => setHasConsent(event.target.checked)} />
        <span>I consent to sending this image to YouCam for cosmetic skin analysis.</span>
      </label>

      {hasConsent && (
        <>
          <CameraKitCapture onCaptured={setImage} />

          <label className="upload-control">
            <span><Upload size={15} aria-hidden="true" /> Upload a JPEG or PNG sample</span>
            <input
              type="file"
              accept="image/jpeg,image/png"
              onChange={(event) => setImage(event.target.files?.[0] ?? null)}
            />
          </label>

          <fieldset className="concern-select">
            <legend>SD concerns ({selectedConcerns.length} of {skinConcerns.length} selected)</legend>
            <div className="concern-options">
              {skinConcerns.map((concern) => (
                <label key={concern.value}>
                  <input
                    type="checkbox"
                    checked={selectedConcerns.includes(concern.value)}
                    onChange={(event) => updateConcern(concern.value, event.target.checked)}
                  />
                  {concern.label}
                </label>
              ))}
            </div>
          </fieldset>

          {image && (
            <div className="capture-preview">
              <div>
                <strong>{image.name}</strong>
                <span>{Math.ceil(image.size / 1024)} KB</span>
              </div>
            </div>
          )}

          <div className="dialog-actions">
            <button className="primary-button" type="button" disabled={!image || selectedConcerns.length === 0 || isAnalyzing} onClick={() => void analyzeImage()}>
              {isAnalyzing ? 'Analyzing…' : 'Analyze sample'}
            </button>
          </div>
        </>
      )}

      {error && <div className="notice notice-error" role="alert"><CircleAlert size={14} /> {error}</div>}

      {result && (
        <section className="analysis-result" aria-labelledby="analysis-result-title">
          <div className="result-heading">
            <Check size={15} aria-hidden="true" />
            <h3 id="analysis-result-title">Analysis complete</h3>
            <span>{result.mode} skincare</span>
          </div>
          <p className="result-units">Published unit estimate: {result.documentedUnitEstimate}. Actual billing/remaining units must be checked in your API account.</p>
          <div className="score-list">
            {result.scores.map((score) => (
              <div className="score-row" key={score.concern}>
                <strong>{skinConcerns.find((concern) => concern.action === score.concern)?.label ?? score.concern}</strong>
                <span>Raw {score.rawScore?.toFixed(1) ?? 'n/a'}</span>
                <span>UI {score.uiScore ?? 'n/a'}</span>
                <span>{score.maskUrls.length} mask{score.maskUrls.length === 1 ? '' : 's'}</span>
              </div>
            ))}
          </div>
          <p className="score-note"><Info size={13} aria-hidden="true" /> Raw scores are the tracking input; YouCam describes UI scores as favorably calibrated.</p>
        </section>
      )}
    </section>
  )
}