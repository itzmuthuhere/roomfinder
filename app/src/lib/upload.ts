import { currentOwnerId } from './api'

export type Media = {
  id: number
  pgId: number
  roomId: number | null
  url: string
  kind: 'PHOTO' | 'VIDEO'
  sortOrder: number
  isCover: boolean
}

export const MAX_VIDEO_BYTES = 50 * 1024 * 1024
export const MAX_VIDEO_SECONDS = 60

/**
 * Downscales a photo in the browser before upload: smaller files on a phone connection,
 * and re-encoding through a canvas drops EXIF, so the PG's GPS location never leaves the device.
 * imageOrientation 'from-image' applies the EXIF rotation first, so portrait photos stay upright.
 */
export async function downscalePhoto(file: File, maxEdge = 1600, quality = 0.82): Promise<Blob> {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height))
  const w = Math.round(bitmap.width * scale)
  const h = Math.round(bitmap.height * scale)

  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas not available')
  ctx.drawImage(bitmap, 0, 0, w, h)
  bitmap.close()

  const blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/jpeg', quality),
  )
  if (!blob) throw new Error('Could not process this photo')
  return blob
}

/** Reads a video's duration locally so we reject a long clip before uploading 50MB. */
export function videoDuration(file: File): Promise<number> {
  return new Promise((resolve, reject) => {
    const el = document.createElement('video')
    el.preload = 'metadata'
    el.onloadedmetadata = () => {
      URL.revokeObjectURL(el.src)
      resolve(el.duration)
    }
    el.onerror = () => reject(new Error('Could not read this video'))
    el.src = URL.createObjectURL(file)
  })
}

/** fetch has no upload progress event, so uploads go through XHR. */
export function uploadFile(
  path: string,
  blob: Blob,
  filename: string,
  onProgress?: (percent: number) => void,
): Promise<Media> {
  return new Promise((resolve, reject) => {
    const form = new FormData()
    form.append('file', blob, filename)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', `/api${path}`)
    const ownerId = currentOwnerId()
    if (ownerId) xhr.setRequestHeader('X-Owner-Id', String(ownerId))

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100))
    }
    xhr.onload = () => {
      let body: any = null
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        /* non-JSON error page */
      }
      if (xhr.status >= 200 && xhr.status < 300) resolve(body as Media)
      else reject(new Error(body?.message ?? `Upload failed (${xhr.status})`))
    }
    xhr.onerror = () => reject(new Error('Upload failed. Check your connection.'))
    xhr.send(form)
  })
}
