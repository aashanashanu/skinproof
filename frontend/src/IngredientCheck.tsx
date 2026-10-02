import { useState } from 'react'
import { CircleAlert, FlaskConical, Info, X } from 'lucide-react'

type IngredientMatch = {
  input: string
  recognized: boolean
  canonicalName: string | null
  irritationGroup: string | null
  sensitizing: boolean
  commonlyUsedFor: string
}

type IngredientConflict = {
  type: 'GROUP_OVERLAP' | 'KNOWN_INTERACTION' | 'SENSITIZING'
  ingredients: string[]
  note: string
}

type IngredientCheckResult = {
  matches: IngredientMatch[]
  conflicts: IngredientConflict[]
  disclaimer: string
}

type IngredientCheckProps = {
  onClose: () => void
}

function parseIngredientInput(raw: string) {
  return raw
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

export function IngredientCheck({ onClose }: IngredientCheckProps) {
  const [rawInput, setRawInput] = useState('')
  const [result, setResult] = useState<IngredientCheckResult | null>(null)
  const [isChecking, setIsChecking] = useState(false)
  const [error, setError] = useState('')

  async function checkIngredients() {
    const ingredients = parseIngredientInput(rawInput)
    if (ingredients.length === 0) return
    setIsChecking(true)
    setError('')

    try {
      const response = await fetch('/api/ingredient-check', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ingredients }),
      })
      if (!response.ok) throw new Error('Ingredient check could not be completed.')
      setResult(await response.json() as IngredientCheckResult)
    } catch {
      setError('Couldn’t check these ingredients. Try again.')
    } finally {
      setIsChecking(false)
    }
  }

  return (
    <section className="dialog ingredient-dialog" role="dialog" aria-modal="true" aria-labelledby="ingredient-dialog-title">
      <div className="dialog-heading">
        <div>
          <p className="eyebrow">STARTER REFERENCE</p>
          <h2 id="ingredient-dialog-title">Ingredient check</h2>
        </div>
        <button className="icon-button close-button" type="button" aria-label="Close ingredient check" onClick={onClose}><X size={17} /></button>
      </div>
      <p className="dialog-intro">
        Paste a product's ingredient list (comma or line separated). SkinProof matches common actives against a
        starter reference and flags known combinations some people find irritating. This is not medical advice.
      </p>

      <label className="form-label">
        Ingredient list
        <textarea
          rows={4}
          placeholder="Niacinamide, Hyaluronic Acid, Retinol…"
          value={rawInput}
          onChange={(event) => setRawInput(event.target.value)}
        />
      </label>

      {error && <p className="capture-error"><CircleAlert size={13} aria-hidden="true" /> {error}</p>}

      <div className="dialog-actions">
        <button
          className="primary-button"
          type="button"
          disabled={parseIngredientInput(rawInput).length === 0 || isChecking}
          onClick={() => void checkIngredients()}
        >
          {isChecking ? 'Checking…' : 'Check ingredients'}
        </button>
      </div>

      {result && (
        <section className="ingredient-results" aria-live="polite">
          <div className="ingredient-match-list">
            {result.matches.map((match, index) => (
              <div className={`ingredient-match-row ${match.recognized ? '' : 'is-unrecognized'}`} key={`${match.input}-${index}`}>
                <div className="ingredient-match-icon" aria-hidden="true"><FlaskConical size={14} /></div>
                <div>
                  <strong>{match.canonicalName ?? match.input}</strong>
                  <p>{match.commonlyUsedFor}</p>
                </div>
              </div>
            ))}
          </div>

          {result.conflicts.length > 0 && (
            <ul className="ingredient-conflict-list">
              {result.conflicts.map((conflict, index) => (
                <li key={index} className={`ingredient-conflict ingredient-conflict-${conflict.type.toLowerCase()}`}>
                  <CircleAlert size={13} aria-hidden="true" />
                  <span>{conflict.note}</span>
                </li>
              ))}
            </ul>
          )}

          <p className="score-note"><Info size={13} aria-hidden="true" /> {result.disclaimer}</p>
        </section>
      )}
    </section>
  )
}
