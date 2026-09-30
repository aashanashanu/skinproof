import { useEffect, useState, type FormEvent } from 'react'
import { Archive, Camera, Clock3, FlaskConical, Info, Plus, Sparkles, X } from 'lucide-react'
import { CameraSpikeView } from './CameraSpikeView'
import { ExperimentCaptureDialog } from './ExperimentCaptureDialog'
import { skinConcerns } from './skinConcerns'
import './App.css'

type Experiment = {
  id: string
  productName: string
  targetConcern: string
  startDate: string
  durationWeeks: number
  protocolNotes: string
  status: 'ACTIVE' | 'ARCHIVED'
}

type ExperimentInput = Omit<Experiment, 'id' | 'status'> & {
  protocolNotes: string
}

const today = new Date().toISOString().slice(0, 10)
const workspaceKey = 'skinproof-demo-workspace'

function getDemoWorkspaceId() {
  let workspaceId = localStorage.getItem(workspaceKey)
  if (!workspaceId) {
    workspaceId = crypto.randomUUID()
    localStorage.setItem(workspaceKey, workspaceId)
  }
  return workspaceId
}

function App() {
  const [experiments, setExperiments] = useState<Experiment[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isCameraOpen, setIsCameraOpen] = useState(false)
  const [captureExperiment, setCaptureExperiment] = useState<Experiment | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')

  async function loadExperiments() {
    try {
      const response = await fetch('/api/experiments', {
        headers: { 'X-Demo-Workspace': getDemoWorkspaceId() },
      })
      if (!response.ok) throw new Error('The experiment service is unavailable.')
      setExperiments(await response.json() as Experiment[])
      setError('')
    } catch {
      setError('Can’t reach the SkinProof service. Check the hosted API and try again.')
    } finally {
      setIsLoading(false)
    }
  }

  async function retryLoadingExperiments() {
    setIsLoading(true)
    await loadExperiments()
  }

  useEffect(() => {
    let isCurrent = true

    fetch('/api/experiments', {
      headers: { 'X-Demo-Workspace': getDemoWorkspaceId() },
    })
      .then(async (response) => {
        if (!response.ok) throw new Error('The experiment service is unavailable.')
        return await response.json() as Experiment[]
      })
      .then((data) => {
        if (!isCurrent) return
        setExperiments(data)
        setError('')
      })
      .catch(() => {
        if (isCurrent) setError('Can’t reach the SkinProof service. Check the hosted API and try again.')
      })
      .finally(() => {
        if (isCurrent) setIsLoading(false)
      })

    return () => {
      isCurrent = false
    }
  }, [])

  async function createExperiment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSaving(true)
    setError('')

    const form = new FormData(event.currentTarget)
    const experiment: ExperimentInput = {
      productName: String(form.get('productName')).trim(),
      targetConcern: String(form.get('targetConcern')),
      startDate: String(form.get('startDate')),
      durationWeeks: Number(form.get('durationWeeks')),
      protocolNotes: String(form.get('protocolNotes')).trim(),
    }

    try {
      const response = await fetch('/api/experiments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Demo-Workspace': getDemoWorkspaceId(),
        },
        body: JSON.stringify(experiment),
      })
      if (!response.ok) throw new Error('The experiment could not be saved.')
      const saved = await response.json() as Experiment
      setExperiments((current) => [saved, ...current])
      setIsFormOpen(false)
    } catch {
      setError('Couldn’t save this experiment. Check the service connection and try again.')
    } finally {
      setIsSaving(false)
    }
  }

  async function archiveExperiment(id: string) {
    setError('')

    try {
      const response = await fetch(`/api/experiments/${id}/archive`, {
        method: 'POST',
        headers: { 'X-Demo-Workspace': getDemoWorkspaceId() },
      })
      if (!response.ok) throw new Error('The experiment could not be archived.')
      setExperiments((current) => current.map((experiment) => (
        experiment.id === id ? { ...experiment, status: 'ARCHIVED' } : experiment
      )))
    } catch {
      setError('Couldn’t archive this experiment. Try again.')
    }
  }

  const activeExperiments = experiments.filter((experiment) => experiment.status === 'ACTIVE')
  const archivedExperiments = experiments.filter((experiment) => experiment.status === 'ARCHIVED')

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="SkinProof home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>skinproof</span>
        </a>
        <div className="sidebar-label">Workspace</div>
        <nav aria-label="Main navigation">
          <a className="nav-item is-current" href="#experiments">
            <FlaskConical className="nav-icon" aria-hidden="true" />
            Experiments
          </a>
          <button className={`nav-item ${isCameraOpen ? 'is-current' : ''}`} type="button" onClick={() => setIsCameraOpen(true)}>
            <Camera className="nav-icon" aria-hidden="true" />
            Camera test
          </button>
        </nav>
        <div className="sidebar-bottom">
          <span className="avatar" aria-hidden="true">SP</span>
          <div>
            <strong>Demo workspace</strong>
            <span>This browser profile</span>
          </div>
        </div>
      </aside>

      <main className="main-content" id="experiments">
        <header className="topbar">
          <span className="breadcrumb">Workspace <span>/</span> Experiments</span>
          <span className="privacy-status"><span aria-hidden="true">●</span> Isolated demo data</span>
        </header>

        <section className="page-heading">
          <div>
            <p className="eyebrow">YOUR SKIN, MEASURED</p>
            <h1>Experiments</h1>
            <p className="page-summary">Track a product against a consistent capture routine.</p>
          </div>
          <button className="primary-button" type="button" onClick={() => setIsFormOpen(true)}>
            <Plus size={15} aria-hidden="true" /> New experiment
          </button>
        </section>

        {error && (
          <div className="notice notice-error" role="alert">
            <span>{error}</span>
            <button type="button" className="text-button" onClick={() => void retryLoadingExperiments()}>Retry</button>
          </div>
        )}

        <section className="experiment-section" aria-labelledby="active-heading">
          <div className="section-heading">
            <div className="section-title-group">
              <h2 id="active-heading">Active</h2>
              <span className="count-badge">{activeExperiments.length}</span>
            </div>
            <span className="section-caption">Ongoing product trials</span>
          </div>

          {isLoading ? (
            <div className="empty-state" role="status">Loading experiments…</div>
          ) : activeExperiments.length === 0 ? (
            <div className="empty-state">
              <div className="empty-mark" aria-hidden="true"><Sparkles size={19} /></div>
              <h3>No experiments yet</h3>
              <p>Start with one product and one skin concern. Your baseline comes first.</p>
              <button className="secondary-button" type="button" onClick={() => setIsFormOpen(true)}>
                Create your first experiment
              </button>
            </div>
          ) : (
            <div className="experiment-list">
              {activeExperiments.map((experiment) => (
                <article className="experiment-row" key={experiment.id}>
                  <div className="experiment-symbol" aria-hidden="true"><FlaskConical size={16} /></div>
                  <div className="experiment-details">
                    <h3>{experiment.productName}</h3>
                    <p>{skinConcerns.find((concern) => concern.value === experiment.targetConcern)?.label ?? experiment.targetConcern}</p>
                  </div>
                  <div className="experiment-meta">
                    <span className="status-dot"><span aria-hidden="true">●</span> Setting up</span>
                    <span>Started {new Date(`${experiment.startDate}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</span>
                  </div>
                  <div className="row-actions">
                    <button className="capture-row-button" type="button" onClick={() => setCaptureExperiment(experiment)}>
                      <Camera size={14} aria-hidden="true" /> Capture
                    </button>
                    <button
                      className="icon-button archive-button"
                      type="button"
                      aria-label={`Archive ${experiment.productName}`}
                      title="Archive experiment"
                      onClick={() => void archiveExperiment(experiment.id)}
                    >
                      <Archive size={15} aria-hidden="true" />
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        {archivedExperiments.length > 0 && (
          <section className="experiment-section archived-section" aria-labelledby="archived-heading">
            <div className="section-heading">
              <div className="section-title-group">
                <h2 id="archived-heading">Archived</h2>
                <span className="count-badge">{archivedExperiments.length}</span>
              </div>
            </div>
            <div className="experiment-list">
              {archivedExperiments.map((experiment) => (
                <article className="experiment-row is-archived" key={experiment.id}>
                  <div className="experiment-symbol" aria-hidden="true"><FlaskConical size={16} /></div>
                  <div className="experiment-details">
                    <h3>{experiment.productName}</h3>
                    <p>{skinConcerns.find((concern) => concern.value === experiment.targetConcern)?.label ?? experiment.targetConcern}</p>
                  </div>
                  <span className="archived-label">Archived</span>
                </article>
              ))}
            </div>
          </section>
        )}

        <footer className="disclaimer">
          <Info size={13} aria-hidden="true" /> SkinProof tracks cosmetic trends, not medical advice.
        </footer>
      </main>

      {isFormOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget && !isSaving) setIsFormOpen(false)
        }}>
          <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title">
            <div className="dialog-heading">
              <div>
                <p className="eyebrow">NEW TRIAL</p>
                <h2 id="dialog-title">Set up an experiment</h2>
              </div>
              <button className="icon-button close-button" type="button" aria-label="Close" onClick={() => setIsFormOpen(false)}><X size={17} /></button>
            </div>
            <p className="dialog-intro">Choose one product and one concern to keep your comparison focused.</p>
            <form className="experiment-form" onSubmit={(event) => void createExperiment(event)}>
              <label>
                Product name
                <input name="productName" placeholder="e.g. Daily barrier serum" maxLength={120} required autoFocus />
              </label>
              <label>
                Focus concern
                <select name="targetConcern" defaultValue="TEXTURE" required>
                  {skinConcerns.map((concern) => <option value={concern.value} key={concern.value}>{concern.label}</option>)}
                </select>
                <span className="field-note">Initial demo options; will be aligned to verified YouCam outputs.</span>
              </label>
              <div className="form-grid">
                <label>
                  Start date
                  <input name="startDate" type="date" defaultValue={today} required />
                </label>
                <label>
                  Trial length
                  <select name="durationWeeks" defaultValue="8">
                    {[4, 6, 8, 10, 12].map((weeks) => <option value={weeks} key={weeks}>{weeks} weeks</option>)}
                  </select>
                </label>
              </div>
              <label>
                Protocol notes <span className="optional-label">Optional</span>
                <textarea name="protocolNotes" rows={3} placeholder="Same time of day, same lighting…" maxLength={500} />
              </label>
              <div className="protocol-note">
                <Clock3 size={14} aria-hidden="true" />
                <p>For a fair comparison, keep your capture time and lighting consistent. Avoid starting another new product during this trial.</p>
              </div>
              <div className="dialog-actions">
                <button className="secondary-button" type="button" disabled={isSaving} onClick={() => setIsFormOpen(false)}>Cancel</button>
                <button className="primary-button" type="submit" disabled={isSaving}>
                  {isSaving ? 'Creating…' : 'Create experiment'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}

      {isCameraOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setIsCameraOpen(false)
        }}>
          <div className="dialog camera-dialog" role="dialog" aria-modal="true" aria-labelledby="camera-spike-title">
            <CameraSpikeView onClose={() => setIsCameraOpen(false)} />
          </div>
        </div>
      )}

      {captureExperiment && (
        <ExperimentCaptureDialog
          experiment={captureExperiment}
          workspaceId={getDemoWorkspaceId()}
          onClose={() => setCaptureExperiment(null)}
        />
      )}
    </div>
  )
}

export default App
