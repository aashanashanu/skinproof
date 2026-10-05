import { useEffect, useState, type FormEvent } from 'react'
import { Bell, X } from 'lucide-react'
import { apiHeaders } from './auth'

type ReminderPreferences = {
  enabled: boolean
  timezone: string
  weekday: number
  preferredTime: string
  cadence: 'WEEKLY' | 'BIWEEKLY'
}

const defaults: ReminderPreferences = {
  enabled: false,
  timezone: 'America/Chicago',
  weekday: 1,
  preferredTime: '09:00',
  cadence: 'WEEKLY',
}

const weekdays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

export function ReminderPreferencesDialog({ workspaceId, onClose }: { workspaceId: string; onClose: () => void }) {
  const [preferences, setPreferences] = useState(defaults)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    let active = true
    async function load() {
      try {
        const response = await fetch('/api/account/reminder-preferences', {
          headers: await apiHeaders(workspaceId),
        })
        if (!response.ok) throw new Error('Reminder preferences could not be loaded.')
        const body = await response.json() as ReminderPreferences
        if (active) setPreferences(body)
      } catch (loadError) {
        if (active) setError(loadError instanceof Error ? loadError.message : 'Reminder preferences could not be loaded.')
      } finally {
        if (active) setIsLoading(false)
      }
    }
    void load()
    return () => { active = false }
  }, [workspaceId])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSaving(true)
    setError('')
    setSaved(false)
    try {
      const response = await fetch('/api/account/reminder-preferences', {
        method: 'PUT',
        headers: await apiHeaders(workspaceId, { 'Content-Type': 'application/json' }),
        body: JSON.stringify(preferences),
      })
      const body = await response.json() as ReminderPreferences & { detail?: string }
      if (!response.ok) throw new Error(body.detail ?? 'Reminder preferences could not be saved.')
      setPreferences(body)
      setSaved(true)
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Reminder preferences could not be saved.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !isSaving) onClose()
    }}>
      <section className="dialog reminder-dialog" role="dialog" aria-modal="true" aria-labelledby="reminder-dialog-title">
        <div className="dialog-heading">
          <div>
            <p className="eyebrow">ACCOUNT SETTINGS</p>
            <h2 id="reminder-dialog-title"><Bell size={17} aria-hidden="true" /> Reminder preferences</h2>
          </div>
          <button className="icon-button close-button" type="button" aria-label="Close" disabled={isSaving} onClick={onClose}><X size={17} /></button>
        </div>
        <p className="notice notice-info reminder-delivery-note">Email delivery is not active. Saving an opt-in records your preference only; no messages are sent.</p>
        {error && <p className="capture-error" role="alert">{error}</p>}
        <form className="experiment-form" onSubmit={(event) => void save(event)}>
          <label className="share-image-choice reminder-opt-in">
            <input
              type="checkbox"
              checked={preferences.enabled}
              onChange={(event) => setPreferences((current) => ({ ...current, enabled: event.target.checked }))}
              disabled={isLoading || isSaving}
            />
            <span>Opt in to generic check-in reminders</span>
          </label>
          <div className="form-grid">
            <label>
              Day
              <select value={preferences.weekday} onChange={(event) => setPreferences((current) => ({ ...current, weekday: Number(event.target.value) }))} disabled={isLoading || isSaving}>
                {weekdays.map((day, index) => <option value={index + 1} key={day}>{day}</option>)}
              </select>
            </label>
            <label>
              Local time
              <input type="time" value={preferences.preferredTime} onChange={(event) => setPreferences((current) => ({ ...current, preferredTime: event.target.value }))} disabled={isLoading || isSaving} required />
            </label>
          </div>
          <label>
            Time zone (IANA)
            <input value={preferences.timezone} onChange={(event) => setPreferences((current) => ({ ...current, timezone: event.target.value }))} disabled={isLoading || isSaving} maxLength={64} required />
          </label>
          <label>
            Cadence
            <select value={preferences.cadence} onChange={(event) => setPreferences((current) => ({ ...current, cadence: event.target.value as ReminderPreferences['cadence'] }))} disabled={isLoading || isSaving}>
              <option value="WEEKLY">Weekly</option>
              <option value="BIWEEKLY">Every two weeks</option>
            </select>
          </label>
          {saved && <p className="reminder-save-status" role="status">Preferences saved.</p>}
          <div className="dialog-actions">
            <button className="secondary-button" type="button" disabled={isSaving} onClick={onClose}>Close</button>
            <button className="primary-button" type="submit" disabled={isLoading || isSaving}>
              {isLoading ? 'Loading…' : isSaving ? 'Saving…' : 'Save preferences'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}