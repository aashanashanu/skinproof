import { useEffect, useState } from 'react'
import { Download, Info, Share2 } from 'lucide-react'

type SharedCapture = {
  captureNumber: number
  takenAt: string
  baseline: boolean
  simulated: boolean
  rawScore: number | null
}

type SharedReport = {
  productName: string
  concern: string
  startDate: string
  durationWeeks: number
  createdAt: string
  simulated: boolean
  disclaimer: string
  verdict: null | {
    label: string
    confidence: string
    baselineMean: number | null
    currentMean: number | null
    delta: number | null
    noiseFloor: number
    validCaptureCount: number
    elapsedWeeks: number
    explanation: string
  }
  captures: SharedCapture[]
}

type SharedReportPayload = {
  report: SharedReport
  expiresAt: string
  imageUrls: Record<string, string>
}

const verdictLabels: Record<string, string> = {
  IMPROVED: 'Improved',
  NO_DETECTABLE_CHANGE: 'No detectable change',
  WORSE: 'Worse',
  INCONCLUSIVE: 'Inconclusive',
}

export function SharedReportView({ token, embed = false }: { token: string; embed?: boolean }) {
  const [payload, setPayload] = useState<SharedReportPayload | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    let active = true
    document.title = 'Shared SkinProof report'
    let robots = document.querySelector<HTMLMetaElement>('meta[name="robots"]')
    if (!robots) {
      robots = document.createElement('meta')
      robots.name = 'robots'
      document.head.appendChild(robots)
    }
    robots.content = 'noindex,nofollow,noarchive'

    async function load() {
      try {
        const response = await fetch(`/api/shared-reports/${encodeURIComponent(token)}`, {
          cache: 'no-store',
          headers: { Accept: 'application/json' },
        })
        if (!response.ok) throw new Error('This report link is unavailable. It may have expired or been revoked.')
        const body = await response.json() as SharedReportPayload
        if (active) setPayload(body)
      } catch (loadError) {
        if (active) setError(loadError instanceof Error ? loadError.message : 'This report link is unavailable.')
      } finally {
        if (active) setIsLoading(false)
      }
    }

    void load()
    return () => { active = false }
  }, [token])

  async function copyEmbed() {
    const source = `${window.location.origin}/?share=${encodeURIComponent(token)}&embed=1`
    const markup = `<iframe src="${source.replaceAll('&', '&amp;')}" title="Shared SkinProof report" loading="lazy" referrerpolicy="no-referrer" style="width:100%;height:560px;border:0"></iframe>`
    try {
      await navigator.clipboard.writeText(markup)
      setCopied(true)
    } catch {
      setError('Could not copy embed code. Use the browser address bar to open this read-only report.')
    }
  }

  if (isLoading) {
    return <main className={`shared-report-shell ${embed ? 'is-embed' : ''}`} role="status">Loading shared report…</main>
  }
  if (error || !payload) {
    return (
      <main className={`shared-report-shell ${embed ? 'is-embed' : ''}`}>
        <p className="eyebrow">SHARED REPORT</p>
        <h1>Report unavailable</h1>
        <p className="page-summary">{error || 'This report link is unavailable.'}</p>
      </main>
    )
  }

  const report = payload.report
  return (
    <main className={`shared-report-shell ${embed ? 'is-embed' : ''}`}>
      <header className="shared-report-header shared-report-no-print">
        <a className="brand" href="/" aria-label="SkinProof home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>skinproof</span>
        </a>
        <div className="shared-report-actions">
          {!embed && <button className="secondary-button" type="button" onClick={() => void copyEmbed()}><Share2 size={14} aria-hidden="true" /> {copied ? 'Embed copied' : 'Copy embed'}</button>}
          <button className="secondary-button" type="button" onClick={() => window.print()}><Download size={14} aria-hidden="true" /> Save as PDF</button>
        </div>
      </header>

      {report.simulated && <div className="notice notice-info" role="status">Synthetic demo data. This is not a real analysis outcome.</div>}
      <p className="eyebrow">SHARED COSMETIC TREND REPORT</p>
      <h1>{report.productName}</h1>
      <p className="page-summary">Focus: {report.concern.replaceAll('_', ' ').toLowerCase()} · Started {new Date(`${report.startDate}T00:00:00`).toLocaleDateString()}</p>

      {report.verdict ? (
        <section className="verdict-panel shared-report-verdict" aria-labelledby="shared-verdict-heading">
          <div className="verdict-heading-row">
            <h2 id="shared-verdict-heading" className={`verdict-label verdict-${report.verdict.label.toLowerCase()}`}>
              {verdictLabels[report.verdict.label] ?? report.verdict.label}
            </h2>
            <span className={`confidence-badge confidence-${report.verdict.confidence.toLowerCase()}`}>
              {report.verdict.confidence.toLowerCase()} confidence
            </span>
          </div>
          <p className="verdict-explanation">{report.verdict.explanation}</p>
          <div className="verdict-stats">
            <span>Baseline mean {report.verdict.baselineMean?.toFixed(1) ?? 'n/a'}</span>
            <span>Current mean {report.verdict.currentMean?.toFixed(1) ?? 'n/a'}</span>
            <span>Raw-score change {report.verdict.delta?.toFixed(1) ?? 'n/a'}</span>
            <span>Noise floor {report.verdict.noiseFloor.toFixed(1)}</span>
            <span>{report.verdict.validCaptureCount} valid captures</span>
          </div>
        </section>
      ) : (
        <div className="empty-state"><h2>No verdict is available yet</h2><p>There is not enough analysis history for a trend summary.</p></div>
      )}

      <section className="shared-report-captures" aria-labelledby="shared-captures-heading">
        <h2 id="shared-captures-heading">Capture history</h2>
        <div className="score-list">
          {report.captures.map((capture) => (
            <article className="shared-capture-row" key={capture.captureNumber}>
              <span>{new Date(capture.takenAt).toLocaleDateString()} · {capture.baseline ? 'Baseline' : 'Follow-up'}</span>
              <strong>Raw {capture.rawScore?.toFixed(1) ?? 'n/a'}</strong>
              {payload.imageUrls[String(capture.captureNumber)] && (
                <img className="shared-capture-image" src={payload.imageUrls[String(capture.captureNumber)]} alt="Shared capture image, included by the report owner" />
              )}
            </article>
          ))}
        </div>
      </section>
      <p className="score-note"><Info size={13} aria-hidden="true" /> {report.disclaimer}</p>
      <p className="shared-report-expiry">Link expires {new Date(payload.expiresAt).toLocaleString()}.</p>
    </main>
  )
}
