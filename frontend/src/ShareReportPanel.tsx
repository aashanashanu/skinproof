import { useEffect, useState } from 'react'
import { Copy, Link2, LockKeyhole, ShieldCheck, X } from 'lucide-react'
import { apiHeaders } from './auth'

type CreateShareResponse = {
  reportId: string
  token: string
  expiresAt: string
  includesImages: boolean
}

type ShareRecord = {
  reportId: string
  createdAt: string
  expiresAt: string
  revokedAt: string | null
  includesImages: boolean
}

export function ShareReportPanel({ experimentId, workspaceId }: { experimentId: string; workspaceId: string }) {
  const [includeImages, setIncludeImages] = useState(false)
  const [share, setShare] = useState<CreateShareResponse | null>(null)
  const [records, setRecords] = useState<ShareRecord[]>([])
  const [isWorking, setIsWorking] = useState(false)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState('')

  const shareUrl = share ? `${window.location.origin}/?share=${encodeURIComponent(share.token)}` : ''

  useEffect(() => {
    let active = true
    async function loadRecords() {
      try {
        const response = await fetch(`/api/experiments/${experimentId}/reports`, {
          headers: await apiHeaders(workspaceId),
        })
        if (!response.ok) throw new Error('Share-link status could not be loaded.')
        const body = await response.json() as ShareRecord[]
        if (active) setRecords(body)
      } catch (loadError) {
        if (active) setError(loadError instanceof Error ? loadError.message : 'Share-link status could not be loaded.')
      }
    }
    void loadRecords()
    return () => { active = false }
  }, [experimentId, workspaceId])

  async function createShare() {
    setIsWorking(true)
    setError('')
    try {
      const response = await fetch(`/api/experiments/${experimentId}/reports`, {
        method: 'POST',
        headers: await apiHeaders(workspaceId, { 'Content-Type': 'application/json' }),
        body: JSON.stringify({ includeImages }),
      })
      const body = await response.json() as CreateShareResponse & { detail?: string }
      if (!response.ok) throw new Error(body.detail ?? 'Share link could not be created.')
      setShare(body)
      setRecords((current) => [{
        reportId: body.reportId,
        createdAt: new Date().toISOString(),
        expiresAt: body.expiresAt,
        revokedAt: null,
        includesImages: body.includesImages,
      }, ...current])
      setCopied(false)
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : 'Share link could not be created.')
    } finally {
      setIsWorking(false)
    }
  }

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(shareUrl)
      setCopied(true)
      setError('')
    } catch {
      setError('Could not copy the link. Select and copy it from the field.')
    }
  }

  async function revokeShare(reportId: string) {
    setIsWorking(true)
    setError('')
    try {
      const response = await fetch(`/api/reports/${reportId}`, {
        method: 'DELETE',
        headers: await apiHeaders(workspaceId),
      })
      if (!response.ok) throw new Error('Share link could not be revoked.')
      if (share?.reportId === reportId) {
        setShare(null)
        setIncludeImages(false)
      }
      setRecords((current) => current.map((record) => record.reportId === reportId
        ? { ...record, revokedAt: new Date().toISOString() }
        : record))
    } catch (revokeError) {
      setError(revokeError instanceof Error ? revokeError.message : 'Share link could not be revoked.')
    } finally {
      setIsWorking(false)
    }
  }

  return (
    <section className="share-report-panel" aria-labelledby="share-report-heading">
      <div className="section-heading">
        <div className="section-title-group">
          <h2 id="share-report-heading">Share report</h2>
        </div>
        <span className="section-caption">Read-only · expires after 30 days</span>
      </div>
      {!share ? (
        <>
          <label className="share-image-choice">
            <input type="checkbox" checked={includeImages} onChange={(event) => setIncludeImages(event.target.checked)} />
            <span>Include source capture images</span>
          </label>
          {includeImages && (
            <p className="analysis-explanation share-warning"><LockKeyhole size={13} aria-hidden="true" /> Anyone with the link can view the included images. Each image URL expires after 15 minutes; already issued URLs may remain valid briefly after revocation.</p>
          )}
          <button className="secondary-button" type="button" disabled={isWorking} onClick={() => void createShare()}>
            <Link2 size={14} aria-hidden="true" /> {isWorking ? 'Creating link…' : 'Create share link'}
          </button>
        </>
      ) : (
        <div className="share-link-result">
          <label>
            Report link
            <input readOnly value={shareUrl} onFocus={(event) => event.currentTarget.select()} />
          </label>
          <div className="share-link-meta">
            <span><ShieldCheck size={13} aria-hidden="true" /> {share.includesImages ? 'Images included by request' : 'Images hidden'}</span>
            <span>Expires {new Date(share.expiresAt).toLocaleDateString()}</span>
          </div>
          <div className="share-link-actions">
            <button className="secondary-button" type="button" onClick={() => void copyLink()}>
              <Copy size={14} aria-hidden="true" /> {copied ? 'Copied' : 'Copy link'}
            </button>
            <button className="text-button share-revoke-button" type="button" disabled={isWorking} onClick={() => void revokeShare(share.reportId)}>
              <X size={13} aria-hidden="true" /> {isWorking ? 'Revoking…' : 'Revoke'}
            </button>
          </div>
        </div>
      )}
      {records.length > 0 && (
        <div className="share-record-list" aria-labelledby="share-records-heading">
          <h3 id="share-records-heading">Share-link history</h3>
          {records.map((record) => {
            const expired = Date.parse(record.expiresAt) <= Date.now()
            const unavailable = Boolean(record.revokedAt) || expired
            return (
              <div className="share-record-row" key={record.reportId}>
                <div>
                  <strong>{record.revokedAt ? 'Revoked' : expired ? 'Expired' : 'Active'}</strong>
                  <span>Created {new Date(record.createdAt).toLocaleDateString()} · Expires {new Date(record.expiresAt).toLocaleDateString()}</span>
                  <span>{record.includesImages ? 'Images included' : 'Images hidden'}</span>
                </div>
                {!unavailable && (
                  <button className="text-button share-revoke-button" type="button" disabled={isWorking} onClick={() => void revokeShare(record.reportId)}>
                    <X size={13} aria-hidden="true" /> Revoke
                  </button>
                )}
              </div>
            )
          })}
        </div>
      )}
      {error && <p className="capture-error" role="alert">{error}</p>}
    </section>
  )
}
