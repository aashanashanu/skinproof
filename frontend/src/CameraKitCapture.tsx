import { useEffect, useState } from 'react'
import { Camera, LoaderCircle } from 'lucide-react'

type QualityEvent = {
  hasFace?: boolean
  position?: string
  frontal?: string
  lighting?: string
}

type CapturedImage = {
  image: string | Blob
}

type CaptureEvent = {
  images: CapturedImage[]
}

type CameraKit = {
  init: (options: {
    faceDetectionMode: 'skincare'
    imageFormat: 'base64'
    language: 'enu'
    qualityLevel: 'moderate'
  }) => void
  openCameraKit: () => void
  close: () => void
  addEventListener: (name: string, listener: (event: unknown) => void) => unknown
  removeEventListener: (listenerId: unknown) => void
}

declare global {
  interface Window {
    YMK?: CameraKit
    YMKAsyncInit?: () => void
  }
}

type CameraKitCaptureProps = {
  disabled?: boolean
  onCaptured: (image: File) => void
}

function isCaptureEvent(event: unknown): event is CaptureEvent {
  return typeof event === 'object' && event !== null && Array.isArray((event as CaptureEvent).images)
}

export function CameraKitCapture({ disabled = false, onCaptured }: CameraKitCaptureProps) {
  const [status, setStatus] = useState<'loading' | 'ready' | 'open' | 'error'>('loading')
  const [quality, setQuality] = useState<QualityEvent | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let isMounted = true
    let cameraKit: CameraKit | undefined
    const listenerIds: unknown[] = []

    const setupCameraKit = () => {
      if (!isMounted) return
      cameraKit = window.YMK
      if (!cameraKit) {
        setStatus('error')
        setError('Camera Kit failed to initialize. Check your connection and reload this view.')
        return
      }

      listenerIds.push(cameraKit.addEventListener('faceQualityChanged', (event) => {
        setQuality(event as QualityEvent)
      }))
      listenerIds.push(cameraKit.addEventListener('faceDetectionCaptured', (event) => {
        if (!isCaptureEvent(event) || !event.images[0]?.image) return
        void createCapturedFile(event.images[0].image)
      }))
      listenerIds.push(cameraKit.addEventListener('cameraFailed', () => {
        setStatus('error')
        setError('Camera access failed. Allow camera permission or upload a photo instead.')
      }))
      listenerIds.push(cameraKit.addEventListener('closed', () => setStatus('ready')))
      setStatus('ready')
    }

    async function createCapturedFile(image: string | Blob) {
      try {
        const imageResponse = typeof image === 'string' ? await fetch(image) : undefined
        if (imageResponse && !imageResponse.ok) throw new Error('The captured image could not be read.')
        const blob = image instanceof Blob ? image : await imageResponse!.blob()
        const contentType = blob.type.startsWith('image/') ? blob.type : 'image/jpeg'
        const extension = contentType === 'image/png' ? 'png' : 'jpg'
        onCaptured(new File([blob], `skinproof-camera-${Date.now()}.${extension}`, { type: contentType }))
        cameraKit?.close()
        setStatus('ready')
      } catch {
        setError('The captured image could not be prepared. Please capture again or upload a photo.')
      }
    }

    window.YMKAsyncInit = setupCameraKit
    if (window.YMK) {
      setupCameraKit()
    } else {
      let script = document.querySelector<HTMLScriptElement>('script[data-skinproof-camera-kit]')
      if (!script) {
        script = document.createElement('script')
        script.src = 'https://plugins-media.makeupar.com/v2.5-camera-kit/sdk.js'
        script.async = true
        script.dataset.skinproofCameraKit = 'true'
        script.onerror = () => {
          if (!isMounted) return
          setStatus('error')
          setError('Camera Kit could not be loaded. Use photo upload or try again later.')
        }
        document.head.appendChild(script)
      }
    }

    return () => {
      isMounted = false
      cameraKit?.close()
      listenerIds.forEach((listenerId) => cameraKit?.removeEventListener(listenerId))
      if (window.YMKAsyncInit === setupCameraKit) window.YMKAsyncInit = undefined
    }
  }, [onCaptured])

  function openCamera() {
    if (!window.YMK || disabled) return
    try {
      window.YMK.init({
        faceDetectionMode: 'skincare',
        imageFormat: 'base64',
        language: 'enu',
        qualityLevel: 'moderate',
      })
      window.YMK.openCameraKit()
      setError('')
      setStatus('open')
    } catch {
      setStatus('error')
      setError('Camera Kit could not open. Check browser camera permission and HTTPS access.')
    }
  }

  return (
    <div className="camera-kit-control">
      <div id="YMK-module" className="camera-kit-mount" />
      <button className="secondary-button" type="button" disabled={disabled || status === 'loading'} onClick={openCamera}>
        {status === 'loading' ? <LoaderCircle size={15} className="spinner" aria-hidden="true" /> : <Camera size={15} aria-hidden="true" />}
        {status === 'loading' ? 'Loading Camera Kit…' : status === 'open' ? 'Camera open' : 'Open guided camera'}
      </button>
      {quality && status === 'open' && (
        <div className="quality-readout" aria-live="polite">
          <span className={quality.hasFace ? 'quality-good' : 'quality-bad'}>Face {quality.hasFace ? 'detected' : 'not detected'}</span>
          <span>Position: {quality.position ?? 'checking'}</span>
          <span>Angle: {quality.frontal ?? 'checking'}</span>
          <span>Lighting: {quality.lighting ?? 'checking'}</span>
        </div>
      )}
      {error && <p className="capture-error" role="alert">{error}</p>}
    </div>
  )
}