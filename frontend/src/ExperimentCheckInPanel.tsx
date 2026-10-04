import { useEffect, useState } from 'react'
import { CircleAlert, Info } from 'lucide-react'
import { apiHeaders } from './auth'

type ConfoundCode = 'CAPTURE_GAP' | 'QUALITY_DRIFT' | 'SUN_EXPOSURE' | 'TRAVEL' | 'STRESS' | 'ILLNESS' | 'NEW_PRODUCT' | 'PROTOCOL_CHANGE'

type CheckInSuggestion = {
  code: ConfoundCode
  severity: 'MINOR' | 'MAJOR'
  reason: string
}

type CheckInResponse = {
  checkInId: string
  checkInDate: string
  reportedConfounds: ConfoundCode[]
  automaticSuggestions: CheckInSuggestion[]
  confirmedSuggestions: ConfoundCode[]
  simulated: boolean
}

type ExperimentCheckInPanelProps = {
  experimentId: string
  workspaceId: string
  onSaved: () => void
}

const selfReportOptions: Array<{ code: ConfoundCode; label: string }> = [
  { code: 'SUN_EXPOSURE', label: 'More sun exposure than usual' },
  { code: 'ILLNESS', label: 'Illness during this period' },
  { code: 'TRAVEL', label: 'Travel or a major routine change' },
  { code: 'STRESS', label: 'Higher stress than usual' },
  { code: 'NEW_PRODUCT', label: 'Started another new skincare product' },
  { code: 'PROTOCOL_CHANGE', label: 'Changed how or when I used the product' },
]

function todayDate() {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

function readableCode(code: ConfoundCode) {
  return code.replaceAll('_', ' ').toLowerCase()
}

export function ExperimentCheckInPanel({ experimentId, workspaceId, onSaved }: ExperimentCheckInPanelProps) {
  const [checkIns, setCheckIns] = useState<CheckInResponse[]>([])
  const [checkInDate, setCheckInDate] = useState(todayDate)
  const [reportedConfounds, setReportedConfounds] = useState<ConfoundCode[]>([])
  const [confirmedSuggestions, setConfirmedSuggestions] = useState<ConfoundCode[]>([])
  const [suggestions, setSuggestions] = useState<CheckInSuggestion[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let isCurrent = true

    async function loadCheckIns() {
      try {
        const response = await fetch(`/api/experiments/${experimentId}/check-ins`, {
          headers: await apiHeaders(workspaceId),
        })
        if (!response.ok) throw new Error('Check-ins could not be loaded.')
        const body = await response.json() as CheckInResponse[]
        if (isCurrent) setCheckIns(body)
      } catch {
        if (isCurrent) setError('Couldn’t load check-ins. Try refreshing this page.')
      } finally {
        if (isCurrent) setIsLoading(false)
      }
    }

    void loadCheckIns()
    return () => { isCurrent = false }
  }, [experimentId, workspaceId])

  function toggleCode(codes: ConfoundCode[], code: ConfoundCode, update: (next: ConfoundCode[]) => void) {
    update(codes.includes(code) ? codes.filter((item) => item !== code) : [...codes, code])
  }

  function changeCheckInDate(nextDate: string) {
    setCheckInDate(nextDate)
    setReportedConfounds([])
    setConfirmedSuggestions([])
    setSuggestions([])
  }

  async function saveCheckIn() {
    setIsSaving(true)
    setError('')
    try {
      const response = await fetch(`/api/experiments/${experimentId}/check-ins`, {
        method: 'POST',
        headers: await apiHeaders(workspaceId, { 'Content-Type': 'application/json' }),
        body: JSON.stringify({ checkInDate, reportedConfounds, confirmedSuggestions }),
      })
      const body = await response.json() as CheckInResponse & { detail?: string; message?: string }
      if (!response.ok) throw new Error(body.detail ?? body.message ?? 'Check-in could not be saved.')
      setCheckIns((current) => [...current.filter((item) => item.checkInDate !== body.checkInDate), body]
        .sort((first, second) => first.checkInDate.localeCompare(second.checkInDate)))
      setSuggestions(body.automaticSuggestions)
      setConfirmedSuggestions(body.confirmedSuggestions)
      onSaved()
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Check-in could not be saved.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <section className="check-in-panel" aria-labelledby="check-in-heading">
      <div className="section-heading">
        <div className="section-title-group">
          <h2 id="check-in-heading">Weekly check-in</h2>
        </div>
        <span className="section-caption">Record changes that may affect comparison</span>
      </div>

      <p className="check-in-intro">These notes add context; they do not diagnose a condition or establish why scores changed.</p>
      <label className="check-in-date-label">
        Check-in date
        <input type="date" value={checkInDate} max={todayDate()} onChange={(event) => changeCheckInDate(event.target.value)} />
      </label>

      <fieldset className="check-in-options">
        <legend>Did any of these apply since your last check-in?</legend>
        {selfReportOptions.map((option) => (
          <label key={option.code}>
            <input
              type="checkbox"
              checked={reportedConfounds.includes(option.code)}
              onChange={() => toggleCode(reportedConfounds, option.code, setReportedConfounds)}
            />
            <span>{option.label}</span>
          </label>
        ))}
      </fieldset>

      {suggestions.length > 0 && (
        <fieldset className="check-in-suggestions">
          <legend><CircleAlert size={13} aria-hidden="true" /> Suggestions from capture history</legend>
          <p>These are automatic suggestions, not reported events. Confirm only the suggestions you want included in the verdict context.</p>
          {suggestions.map((suggestion) => (
            <label key={suggestion.code}>
              <input
                type="checkbox"
                checked={confirmedSuggestions.includes(suggestion.code)}
                onChange={() => toggleCode(confirmedSuggestions, suggestion.code, setConfirmedSuggestions)}
              />
              <span><strong>{readableCode(suggestion.code)}</strong> · {suggestion.reason}</span>
            </label>
          ))}
        </fieldset>
      )}

      {error && <div className="notice notice-error" role="alert">{error}</div>}
      <div className="check-in-actions">
        <button className="primary-button" type="button" disabled={isSaving || !checkInDate} onClick={() => void saveCheckIn()}>
          {isSaving ? 'Saving…' : 'Save check-in'}
        </button>
        <span><Info size={13} aria-hidden="true" /> Only self-reported flags and confirmed suggestions affect verdict confidence.</span>
      </div>

      {isLoading ? (
        <p className="analysis-explanation" role="status">Loading check-in history…</p>
      ) : checkIns.length > 0 ? (
        <div className="check-in-history" aria-label="Previous check-ins">
          {checkIns.map((checkIn) => (
            <article key={checkIn.checkInId}>
              <strong>{new Date(`${checkIn.checkInDate}T00:00:00`).toLocaleDateString()}</strong>
              <span>{checkIn.reportedConfounds.length > 0
                ? `Reported: ${checkIn.reportedConfounds.map(readableCode).join(', ')}`
                : 'No changes reported'}</span>
              {checkIn.confirmedSuggestions.length > 0 && (
                <span>Confirmed suggestions: {checkIn.confirmedSuggestions.map(readableCode).join(', ')}</span>
              )}
            </article>
          ))}
        </div>
      ) : null}
    </section>
  )
}
