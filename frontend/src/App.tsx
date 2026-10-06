import { useEffect, useState, type FormEvent } from 'react'
import { Archive, ArchiveRestore, Beaker, Bell, Camera, Clock3, FlaskConical, Info, LogIn, LogOut, Plus, Sparkles, Trash2, X } from 'lucide-react'
import { CameraSpikeView } from './CameraSpikeView'
import { ExperimentCaptureDialog } from './ExperimentCaptureDialog'
import { ExperimentResultsView } from './ExperimentResultsView'
import { IngredientCheck } from './IngredientCheck'
import { apiHeaders, beginSignIn, completeSignInRedirect, currentSession, identityIsConfigured, signOut, type AuthSession } from './auth'
import { SharedReportView } from './SharedReportView'
import { ReminderPreferencesDialog } from './ReminderPreferencesDialog'
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
  const [sharedToken] = useState(() => new URLSearchParams(window.location.search).get('share'))
  const [experiments, setExperiments] = useState<Experiment[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isCameraOpen, setIsCameraOpen] = useState(false)
  const [isIngredientCheckOpen, setIsIngredientCheckOpen] = useState(false)
  const [isResetConfirmOpen, setIsResetConfirmOpen] = useState(false)
  const [experimentToDelete, setExperimentToDelete] = useState<Experiment | null>(null)
  const [isDeletingExperiment, setIsDeletingExperiment] = useState(false)
  const [isReminderSettingsOpen, setIsReminderSettingsOpen] = useState(false)
  const [isSeeding, setIsSeeding] = useState(false)
  const [isResetting, setIsResetting] = useState(false)
  const [authSession, setAuthSession] = useState<AuthSession | null>(null)
  const [isAuthReady, setIsAuthReady] = useState(false)
  const [captureExperiment, setCaptureExperiment] = useState<Experiment | null>(null)
  const [resultsExperiment, setResultsExperiment] = useState<Experiment | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')

  async function loadExperiments() {
    try {
      const response = await fetch('/api/experiments', {
        headers: await apiHeaders(getDemoWorkspaceId()),
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

    async function initialize() {
      try {
        if (sharedToken) return
        if (!identityIsConfigured()) {
          throw new Error('Account sign-in is not configured. Please try again later.')
        }
        const session = await completeSignInRedirect()
        if (!isCurrent) return
        if (!session) {
          setAuthSession(null)
          setExperiments([])
          return
        }
        setAuthSession(session)

        const claimResponse = await fetch('/api/experiments/claim-demo-data', {
          method: 'POST',
          headers: await apiHeaders(getDemoWorkspaceId(), {}, true),
        })
        if (!claimResponse.ok) throw new Error('Your sign-in worked, but demo data could not be claimed. Your previous demo workspace is unchanged.')

        const response = await fetch('/api/experiments', { headers: await apiHeaders(getDemoWorkspaceId()) })
        if (!response.ok) throw new Error('The experiment service is unavailable.')
        if (isCurrent) {
          setExperiments(await response.json() as Experiment[])
          setError('')
        }
      } catch (initializationError) {
        if (isCurrent) {
          setError(initializationError instanceof Error
            ? initializationError.message
            : 'Can’t reach the SkinProof service. Check the hosted API and try again.')
        }
      } finally {
        if (isCurrent) {
          setIsAuthReady(true)
          setIsLoading(false)
        }
      }
    }

    void initialize()

    return () => {
      isCurrent = false
    }
  }, [sharedToken])

  useEffect(() => {
    if (!authSession) return
    let isCurrent = true

    async function validateSession() {
      try {
        const session = await currentSession()
        if (isCurrent && session) setAuthSession(session)
      } catch {
        if (!isCurrent) return
        setAuthSession(null)
        setExperiments([])
        setCaptureExperiment(null)
        setResultsExperiment(null)
        setError('Your sign-in expired. Sign in again to continue.')
      }
    }

    const interval = window.setInterval(() => { void validateSession() }, 30_000)
    return () => {
      isCurrent = false
      window.clearInterval(interval)
    }
  }, [authSession])

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
          ...await apiHeaders(getDemoWorkspaceId(), { 'Content-Type': 'application/json' }),
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
        headers: await apiHeaders(getDemoWorkspaceId()),
      })
      if (!response.ok) throw new Error('The experiment could not be archived.')
      setExperiments((current) => current.map((experiment) => (
        experiment.id === id ? { ...experiment, status: 'ARCHIVED' } : experiment
      )))
    } catch {
      setError('Couldn’t archive this experiment. Try again.')
    }
  }

  async function restoreExperiment(id: string) {
    setError('')
    try {
      const response = await fetch(`/api/experiments/${id}/restore`, {
        method: 'POST',
        headers: await apiHeaders(getDemoWorkspaceId()),
      })
      if (!response.ok) throw new Error('The experiment could not be restored.')
      const restored = await response.json() as Experiment
      setExperiments((current) => current.map((experiment) => experiment.id === id ? restored : experiment))
    } catch {
      setError('Couldn’t restore this experiment. Try again.')
    }
  }

  async function deleteExperiment() {
    if (!experimentToDelete) return
    setIsDeletingExperiment(true)
    setError('')
    try {
      const response = await fetch(`/api/experiments/${experimentToDelete.id}`, {
        method: 'DELETE',
        headers: await apiHeaders(getDemoWorkspaceId()),
      })
      if (!response.ok) throw new Error('The experiment could not be deleted.')
      setExperiments((current) => current.filter((experiment) => experiment.id !== experimentToDelete.id))
      setExperimentToDelete(null)
    } catch {
      setError('Couldn’t delete this experiment. Try again.')
    } finally {
      setIsDeletingExperiment(false)
    }
  }

  async function seedDemoData() {
    setIsSeeding(true)
    setError('')
    try {
      const response = await fetch('/api/demo/seed', {
        method: 'POST',
        headers: await apiHeaders(getDemoWorkspaceId()),
      })
      if (!response.ok) throw new Error('Demo data could not be seeded.')
      const seeded = await response.json() as Experiment[]
      setExperiments((current) => [...seeded, ...current])
    } catch {
      setError('Couldn’t seed demo data. Try again.')
    } finally {
      setIsSeeding(false)
    }
  }

  async function resetDemoData() {
    setIsResetting(true)
    setError('')
    try {
      const response = await fetch(authSession ? '/api/account/data' : '/api/demo/data', {
        method: 'DELETE',
        headers: await apiHeaders(getDemoWorkspaceId()),
      })
      if (!response.ok) throw new Error('Demo data could not be deleted.')
      localStorage.removeItem(workspaceKey)
      setExperiments([])
      setIsResetConfirmOpen(false)
    } catch {
      setError('Couldn’t delete this workspace’s data. Try again.')
    } finally {
      setIsResetting(false)
    }
  }

  async function login() {
    setError('')
    try {
      await beginSignIn()
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : 'Could not start sign-in.')
    }
  }

  if (sharedToken) {
    return <SharedReportView token={sharedToken} embed={new URLSearchParams(window.location.search).get('embed') === '1'} />
  }

  if (!isAuthReady || isLoading) {
    return (
      <main className="auth-gate" aria-live="polite">
        <a className="brand auth-gate-brand" href="/" aria-label="SkinProof home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>skinproof</span>
        </a>
        <p className="eyebrow">ACCOUNT REQUIRED</p>
        <h1>Checking your sign-in</h1>
        <p className="page-summary">Your experiments are available after your account is verified.</p>
      </main>
    )
  }

  if (!authSession) {
    return (
      <main className="auth-gate" aria-labelledby="auth-gate-title">
        <a className="brand auth-gate-brand" href="/" aria-label="SkinProof home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>skinproof</span>
        </a>
        <p className="eyebrow">ACCOUNT REQUIRED</p>
        <h1 id="auth-gate-title">{identityIsConfigured() ? 'Sign in to continue' : 'Sign-in is unavailable'}</h1>
        <p className="page-summary">{identityIsConfigured()
          ? 'Your experiments and skin-analysis results are available only to your signed-in account.'
          : 'Account sign-in has not been configured for this deployment. Please try again later.'}</p>
        {error && <div className="notice notice-error" role="alert">{error}</div>}
        {identityIsConfigured() && (
          <button className="primary-button auth-gate-button" type="button" onClick={() => void login()}>
            <LogIn size={15} aria-hidden="true" /> Sign in with email
          </button>
        )}
        <p className="score-note auth-gate-note"><Info size={13} aria-hidden="true" /> SkinProof tracks cosmetic trends, not medical advice.</p>
      </main>
    )
  }

  async function logout() {
    try {
      await signOut()
    } catch {
      setError('Could not sign out. Close this browser tab to end the session.')
    }
  }

  const activeExperiments = experiments.filter((experiment) => experiment.status === 'ACTIVE')
  const archivedExperiments = experiments.filter((experiment) => experiment.status === 'ARCHIVED')

  if (resultsExperiment) {
    return (
      <div className="app-shell">
        <ExperimentResultsView
          experiment={resultsExperiment}
          workspaceId={getDemoWorkspaceId()}
          onBack={() => setResultsExperiment(null)}
        />
      </div>
    )
  }

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
          <button className={`nav-item ${isIngredientCheckOpen ? 'is-current' : ''}`} type="button" onClick={() => setIsIngredientCheckOpen(true)}>
            <Beaker className="nav-icon" aria-hidden="true" />
            Ingredient check
          </button>
        </nav>
        <div className="sidebar-bottom">
          <span className="avatar" aria-hidden="true">SP</span>
          <div>
            <strong>{authSession ? 'Signed in' : 'Demo workspace'}</strong>
            <span>{authSession?.email || 'This browser profile'}</span>
            {authSession ? (
              <>
                <button className="text-button account-action-link" type="button" onClick={() => void logout()}>
                  <LogOut size={11} aria-hidden="true" /> Sign out
                </button>
                <button className="text-button delete-data-link" type="button" onClick={() => setIsResetConfirmOpen(true)}>
                  <Trash2 size={11} aria-hidden="true" /> Delete my account data
                </button>
              </>
            ) : (
              <>
                {identityIsConfigured() && (
                  <button className="text-button account-action-link" type="button" disabled={!isAuthReady} onClick={() => void login()}>
                    <LogIn size={11} aria-hidden="true" /> Sign in with email
                  </button>
                )}
                <button className="text-button delete-data-link" type="button" onClick={() => setIsResetConfirmOpen(true)}>
                  <Trash2 size={11} aria-hidden="true" /> Delete my demo data
                </button>
              </>
            )}
          </div>
        </div>
      </aside>

      <main className="main-content" id="experiments">
        <header className="topbar">
          <span className="breadcrumb">Workspace <span>/</span> Experiments</span>
          <div className="topbar-actions">
            <span className="privacy-status"><span aria-hidden="true">●</span> {authSession ? `Account · ${authSession.email}` : 'Demo data · not an account'}</span>
            {identityIsConfigured() && (authSession ? (
              <>
                <button className="account-topbar-button" type="button" aria-label="Sign out" title="Sign out" onClick={() => void logout()}>
                  <LogOut size={14} aria-hidden="true" /><span>Sign out</span>
                </button>
                <button className="account-topbar-button" type="button" onClick={() => setIsReminderSettingsOpen(true)}>
                  <Bell size={14} aria-hidden="true" /><span>Reminders</span>
                </button>
                <button className="icon-button account-delete-button" type="button" aria-label="Delete my account data" title="Delete my account data" onClick={() => setIsResetConfirmOpen(true)}>
                  <Trash2 size={14} aria-hidden="true" />
                </button>
              </>
            ) : (
              <button className="account-topbar-button" type="button" disabled={!isAuthReady} onClick={() => void login()}>
                <LogIn size={14} aria-hidden="true" /><span>Sign in</span>
              </button>
            ))}
          </div>
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
              {experiments.length === 0 && !authSession && (
                <button className="text-button seed-demo-button" type="button" disabled={isSeeding} onClick={() => void seedDemoData()}>
                  {isSeeding ? 'Seeding…' : 'Or load three simulated example experiments'}
                </button>
              )}
            </div>
          ) : (
            <div className="experiment-list">
              {activeExperiments.map((experiment) => (
                <article
                  className="experiment-row is-clickable"
                  key={experiment.id}
                  onClick={() => setResultsExperiment(experiment)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault()
                      setResultsExperiment(experiment)
                    }
                  }}
                  role="button"
                  tabIndex={0}
                >
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
                    <button className="capture-row-button" type="button" onClick={(event) => { event.stopPropagation(); setCaptureExperiment(experiment) }}>
                      <Camera size={14} aria-hidden="true" /> Capture
                    </button>
                    <button
                      className="icon-button archive-button"
                      type="button"
                      aria-label={`Archive ${experiment.productName}`}
                      title="Archive experiment"
                      onClick={(event) => { event.stopPropagation(); void archiveExperiment(experiment.id) }}
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
                  <div className="row-actions archived-row-actions">
                    <button className="icon-button archive-button" type="button" aria-label={`Restore ${experiment.productName}`} title="Restore experiment" onClick={() => void restoreExperiment(experiment.id)}>
                      <ArchiveRestore size={15} aria-hidden="true" />
                    </button>
                    <button className="icon-button archived-delete-button" type="button" aria-label={`Delete ${experiment.productName}`} title="Delete experiment permanently" onClick={() => setExperimentToDelete(experiment)}>
                      <Trash2 size={15} aria-hidden="true" />
                    </button>
                  </div>
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
            <CameraSpikeView workspaceId={getDemoWorkspaceId()} onClose={() => setIsCameraOpen(false)} />
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

      {isIngredientCheckOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setIsIngredientCheckOpen(false)
        }}>
          <IngredientCheck onClose={() => setIsIngredientCheckOpen(false)} />
        </div>
      )}

      {isResetConfirmOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget && !isResetting) setIsResetConfirmOpen(false)
        }}>
          <section className="dialog" role="alertdialog" aria-modal="true" aria-labelledby="reset-dialog-title">
            <div className="dialog-heading">
              <div>
                <p className="eyebrow">DELETE {authSession ? 'ACCOUNT' : 'DEMO'} DATA</p>
                <h2 id="reset-dialog-title">Delete my {authSession ? 'account' : 'demo'} data?</h2>
              </div>
              <button className="icon-button close-button" type="button" aria-label="Close" disabled={isResetting} onClick={() => setIsResetConfirmOpen(false)}><X size={17} /></button>
            </div>
            <p className="dialog-intro">
              This permanently deletes every experiment, capture, check-in, verdict, and private image {authSession
                ? 'owned by this signed-in account, along with its reminder preferences.'
                : "stored for this browser's demo workspace."} It cannot be undone.
            </p>
            <div className="dialog-actions">
              <button className="secondary-button" type="button" disabled={isResetting} onClick={() => setIsResetConfirmOpen(false)}>Cancel</button>
              <button className="primary-button delete-confirm-button" type="button" disabled={isResetting} onClick={() => void resetDemoData()}>
                {isResetting ? 'Deleting…' : 'Delete everything'}
              </button>
            </div>
          </section>
        </div>
      )}

      {experimentToDelete && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget && !isDeletingExperiment) setExperimentToDelete(null)
        }}>
          <section className="dialog" role="alertdialog" aria-modal="true" aria-labelledby="delete-experiment-title">
            <div className="dialog-heading">
              <div>
                <p className="eyebrow">PERMANENT DELETE</p>
                <h2 id="delete-experiment-title">Delete {experimentToDelete.productName}?</h2>
              </div>
              <button className="icon-button close-button" type="button" aria-label="Close" disabled={isDeletingExperiment} onClick={() => setExperimentToDelete(null)}><X size={17} /></button>
            </div>
            <p className="dialog-intro">This permanently deletes the experiment, its check-ins and results, capture records, private images, and shared reports. This cannot be undone.</p>
            <div className="dialog-actions">
              <button className="secondary-button" type="button" disabled={isDeletingExperiment} onClick={() => setExperimentToDelete(null)}>Cancel</button>
              <button className="primary-button delete-confirm-button" type="button" disabled={isDeletingExperiment} onClick={() => void deleteExperiment()}>
                {isDeletingExperiment ? 'Deleting…' : 'Delete experiment'}
              </button>
            </div>
          </section>
        </div>
      )}

      {isReminderSettingsOpen && authSession && (
        <ReminderPreferencesDialog workspaceId={getDemoWorkspaceId()} onClose={() => setIsReminderSettingsOpen(false)} />
      )}
    </div>
  )
}

export default App
