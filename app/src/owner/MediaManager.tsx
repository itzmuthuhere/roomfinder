import { useRef, useState } from 'react'
import { api, ApiError, type Bed, type PgDetail } from '../lib/api'
import {
  downscalePhoto,
  uploadFile,
  videoDuration,
  MAX_VIDEO_BYTES,
  MAX_VIDEO_SECONDS,
  type Media,
} from '../lib/upload'

export function MediaManager({ detail, onChanged }: { detail: PgDetail; onChanged: () => void }) {
  const pgId = detail.pg.id
  const photos = detail.media.filter((m) => m.kind === 'PHOTO')
  const video = detail.media.find((m) => m.kind === 'VIDEO')

  const rooms = new Map<number, Bed[]>()
  for (const b of detail.beds) rooms.set(b.roomId, [...(rooms.get(b.roomId) ?? []), b])

  return (
    <section>
      <h4>Photos and video</h4>
      {[...rooms.entries()].map(([roomId, beds]) => (
        <RoomPhotos
          key={roomId}
          pgId={pgId}
          roomId={roomId}
          label={`Room ${beds[0].roomNo ?? roomId} · ${beds[0].sharingType.toLowerCase()}`}
          photos={photos.filter((p) => p.roomId === roomId).sort((a, b) => a.sortOrder - b.sortOrder)}
          onChanged={onChanged}
        />
      ))}
      {rooms.size === 0 && <p className="muted">Add a room first, then you can add its photos.</p>}
      <VideoSlot pgId={pgId} video={video} onChanged={onChanged} />
    </section>
  )
}

function RoomPhotos({
  pgId,
  roomId,
  label,
  photos,
  onChanged,
}: {
  pgId: number
  roomId: number
  label: string
  photos: Media[]
  onChanged: () => void
}) {
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)

  async function pick(files: FileList | null) {
    if (!files?.length) return
    setError(null)
    try {
      for (const file of Array.from(files)) {
        setProgress(0)
        const blob = await downscalePhoto(file)
        await uploadFile(`/pgs/${pgId}/rooms/${roomId}/media/photos`, blob, 'photo.jpg', setProgress)
      }
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setProgress(null)
      if (input.current) input.current.value = ''
    }
  }

  async function act(fn: () => Promise<unknown>) {
    setError(null)
    try {
      await fn()
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  function move(index: number, delta: number) {
    const next = [...photos]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    ;[next[index], next[target]] = [next[target], next[index]]
    void act(() =>
      api.patch(`/pgs/${pgId}/rooms/${roomId}/media/reorder`, { orderedIds: next.map((p) => p.id) }),
    )
  }

  return (
    <div className="room">
      <div className="row">
        <strong>{label}</strong>
        <span className="muted">{photos.length}/10 photos</span>
      </div>
      {error && <p className="bad">{error}</p>}

      <div className="photo-strip">
        {photos.map((p, i) => (
          <figure key={p.id} className={p.isCover ? 'photo cover' : 'photo'}>
            <img src={p.url} alt="" loading="lazy" />
            {p.isCover && <figcaption>Cover</figcaption>}
            <div className="actions">
              <button onClick={() => move(i, -1)} disabled={i === 0} title="Move left">←</button>
              <button onClick={() => move(i, 1)} disabled={i === photos.length - 1} title="Move right">→</button>
              {!p.isCover && (
                <button onClick={() => act(() => api.patch(`/pgs/${pgId}/media/${p.id}/cover`, {}))}>
                  Cover
                </button>
              )}
              <button onClick={() => act(() => api.del(`/media/${p.id}`))}>Delete</button>
            </div>
          </figure>
        ))}
        {photos.length === 0 && <p className="muted">No photos yet.</p>}
      </div>

      {progress !== null && <p className="muted">Uploading… {progress}%</p>}
      <input
        ref={input}
        type="file"
        accept="image/*"
        capture="environment"
        multiple
        disabled={photos.length >= 10 || progress !== null}
        onChange={(e) => pick(e.target.files)}
      />
    </div>
  )
}

function VideoSlot({
  pgId,
  video,
  onChanged,
}: {
  pgId: number
  video: Media | undefined
  onChanged: () => void
}) {
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)

  async function pick(files: FileList | null) {
    const file = files?.[0]
    if (!file) return
    setError(null)
    try {
      if (file.size > MAX_VIDEO_BYTES) {
        throw new Error('Video must be under 50MB. Record at 720p instead of 4K.')
      }
      const seconds = await videoDuration(file)
      if (seconds > MAX_VIDEO_SECONDS + 1) {
        throw new Error(`Video must be ${MAX_VIDEO_SECONDS} seconds or less. This one is ${Math.round(seconds)}s.`)
      }
      if (video && !window.confirm('This replaces the current walkthrough video. Continue?')) return
      setProgress(0)
      await uploadFile(`/pgs/${pgId}/media/video`, file, 'walkthrough.mp4', setProgress)
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setProgress(null)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <div className="room">
      <div className="row">
        <strong>Walkthrough video</strong>
        <span className="muted">one per PG · max {MAX_VIDEO_SECONDS}s · max 50MB</span>
      </div>
      {error && <p className="bad">{error}</p>}
      {video ? (
        <div className="row">
          <video src={video.url} controls playsInline width={280} />
          <button onClick={() => api.del(`/media/${video.id}`).then(onChanged)}>Delete</button>
        </div>
      ) : (
        <p className="muted">No video yet. Walk from the gate to a room, filming in one take.</p>
      )}
      {progress !== null && <p className="muted">Uploading… {progress}%</p>}
      <input
        ref={input}
        type="file"
        accept="video/mp4"
        capture="environment"
        disabled={progress !== null}
        onChange={(e) => pick(e.target.files)}
      />
    </div>
  )
}
