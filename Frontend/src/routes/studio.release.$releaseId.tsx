import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useEffect, useRef, useState } from 'react'
import { useQuery, useSuspenseQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowLeft, ArrowUp, Clock3, Disc3, ExternalLink, Image as ImageIcon, Music2, Save, Send, Trash2, Upload } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import {
  studioReleaseQuery, studioReleaseRevisionQuery, studioReleasesQuery, apiDeleteRelease,
  apiSaveReleaseRevision, apiSubmitReleaseRevision, apiUploadRevisionCover,
  apiUploadRevisionTrackAudio, apiUploadRevisionTrackCover,
} from '../lib/api/queries/studio'
import { releaseTypeLabel, type ReleaseStatus, type StudioReleaseDetail } from '../lib/studio-data'
import type { ReleaseRevisionWire } from '../lib/api/queries/studio'
import { formatDuration } from '../lib/format'
import { useCreatorIdentity } from '../features/studio/use-creator-identity'
import { TrackLyricsEditor } from '../features/studio/track-lyrics-editor'

export const Route = createFileRoute('/studio/release/$releaseId')({
  loader: ({ context: { queryClient }, params: { releaseId } }) =>
    queryClient.ensureQueryData(studioReleaseQuery(releaseId)),
  component: ReleaseManage,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
      <p className="text-sm text-gray-500 dark:text-gray-300">This release no longer exists.</p>
      <Link to="/studio/releases" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to releases</Link>
    </div>
  ),
})

const LABEL = 'text-[11px] font-bold uppercase tracking-[0.15em] text-gray-500 dark:text-gray-400'
const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent p-6 shadow-sm dark:shadow-none'
const INPUT = 'w-full h-11 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 dark:placeholder:text-white/25 focus:outline-none focus:border-beatz-green/60 focus:ring-1 focus:ring-beatz-green/30 transition-all'

const STATUS_META: Record<ReleaseStatus, { label: string; cls: string }> = {
  live: { label: 'Live', cls: 'bg-beatz-green/15 text-beatz-green' },
  scheduled: { label: 'Scheduled', cls: 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' },
  in_review: { label: 'In review', cls: 'bg-beatz-blue/15 text-beatz-blue' },
  draft: { label: 'Draft', cls: 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' },
}

function coverGradient(title: string): string {
  let h = 0
  for (let i = 0; i < title.length; i++) h = (h * 31 + title.charCodeAt(i)) % 360
  return `linear-gradient(135deg, hsl(${h} 55% 42%), hsl(${(h + 48) % 360} 60% 30%))`
}

function revisionToDraft(release: StudioReleaseDetail, revision: ReleaseRevisionWire): StudioReleaseDetail {
  return {
    ...release,
    title: revision.title,
    genre: revision.genre,
    description: revision.description,
    visibility: revision.visibility === 'scheduled' ? 'scheduled' : 'public',
    scheduledAt: revision.scheduledAt,
    coverImage: revision.coverImage,
    downloadable: revision.downloadable,
    tracks: [...revision.tracks]
      .sort((a, b) => a.position - b.position)
      .map((track) => ({
        trackId: track.trackId,
        title: track.title,
        duration: track.durationSec,
        status: track.audioAssetId ? 'uploading' : release.tracks.find((t) => t.trackId === track.trackId)?.status ?? 'ready',
        position: track.position,
        price: track.priceMinor / 100,
        image: track.image,
        customArtwork: track.customArtwork,
      })),
  }
}

function ReleaseManage() {
  const { releaseId } = Route.useParams()
  const navigate = useNavigate()
  const creator = useCreatorIdentity()
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data: release } = useSuspenseQuery(studioReleaseQuery(releaseId))
  const revisionQuery = useQuery({
    ...studioReleaseRevisionQuery(releaseId),
    enabled: release.status !== 'draft',
  })
  const revision = revisionQuery.data
  const [draft, setDraft] = useState<StudioReleaseDetail>(release)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const coverInput = useRef<HTMLInputElement>(null)
  const status = STATUS_META[release.status] ?? { label: release.status, cls: 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' }
  const live = release.status === 'live'
  const creatorId = creator.id
  const editable = release.status !== 'draft' && (release.status === 'in_review' || revision?.status !== 'in_review')
  const pendingRevision = release.status !== 'in_review' && revision?.status === 'in_review'

  useEffect(() => {
    if (revision) {
      setDraft(revisionToDraft(release, revision))
      setDirty(false)
    }
  }, [release, revision])

  const set = <K extends keyof StudioReleaseDetail>(key: K, value: StudioReleaseDetail[K]) => {
    setDraft((current) => ({ ...current, [key]: value }))
    setDirty(true)
  }

  const updateTrack = (trackId: string, patch: Partial<StudioReleaseDetail['tracks'][number]>) => {
    setDraft((current) => ({
      ...current,
      tracks: current.tracks.map((track) => track.trackId === trackId ? { ...track, ...patch } : track),
    }))
    setDirty(true)
  }

  const moveTrack = (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= draft.tracks.length) return
    const tracks = [...draft.tracks]
    ;[tracks[index], tracks[target]] = [tracks[target], tracks[index]]
    setDraft((current) => ({ ...current, tracks: tracks.map((track, position) => ({ ...track, position })) }))
    setDirty(true)
  }

  const save = async () => {
    if (!editable || busy) return
    setBusy(true)
    try {
      await apiSaveReleaseRevision(release.id, {
        title: draft.title,
        genre: draft.genre,
        description: draft.description,
        visibility: draft.visibility,
        scheduledAt: draft.scheduledAt,
        coverImage: draft.coverImage,
        downloadable: draft.downloadable,
        tracks: draft.tracks.map((track, position) => ({
          trackId: track.trackId, position, priceMinor: Math.round(track.price * 100),
          title: track.title, image: track.image,
        })),
      })
      setDirty(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: studioReleaseRevisionQuery(release.id).queryKey }),
        queryClient.invalidateQueries({ queryKey: studioReleaseQuery(release.id).queryKey }),
        queryClient.invalidateQueries({ queryKey: studioReleasesQuery().queryKey }),
      ])
      toast(release.status === 'in_review' ? 'Submission updated' : 'Changes saved as a revision', 'success')
    } catch {
      toast('Could not save release changes. Please try again.', 'error')
    } finally {
      setBusy(false)
    }
  }

  const submitRevision = async () => {
    if (busy) return
    setBusy(true)
    try {
      if (dirty) {
        await apiSaveReleaseRevision(release.id, {
          title: draft.title, genre: draft.genre, description: draft.description,
          visibility: draft.visibility, scheduledAt: draft.scheduledAt,
          coverImage: draft.coverImage, downloadable: draft.downloadable,
          tracks: draft.tracks.map((track, position) => ({
            trackId: track.trackId, position, priceMinor: Math.round(track.price * 100),
            title: track.title, image: track.image,
          })),
        })
      }
      await apiSubmitReleaseRevision(release.id)
      await queryClient.invalidateQueries({ queryKey: studioReleaseRevisionQuery(release.id).queryKey })
      setDirty(false)
      toast('Revision sent for review. Fans will keep seeing the current version until it is approved.', 'success')
    } catch (error) {
      toast(error instanceof Error ? error.message : 'Could not submit changes for review.', 'error')
    } finally {
      setBusy(false)
    }
  }

  const uploadCover = async (file?: File) => {
    if (!file) return
    try {
      const image = await apiUploadRevisionCover(release.id, file)
      set('coverImage', image)
      toast('Cover artwork saved', 'success')
    } catch {
      toast('Could not upload the release artwork.', 'error')
    }
  }

  const uploadTrackCover = async (trackId: string, file?: File) => {
    if (!file) return
    try {
      const image = await apiUploadRevisionTrackCover(release.id, trackId, file)
      updateTrack(trackId, { image, customArtwork: true })
      toast(release.status === 'in_review'
        ? 'Track artwork saved to the submission under review'
        : 'Artwork saved to a draft revision. Send it for review before it appears to fans.', 'success')
    } catch {
      toast('Could not upload track artwork.', 'error')
    }
  }

  const uploadTrackAudio = async (trackId: string, file?: File) => {
    if (!file) return
    try {
      await apiUploadRevisionTrackAudio(release.id, trackId, file)
      updateTrack(trackId, { status: 'uploading' })
      await queryClient.invalidateQueries({ queryKey: studioReleaseRevisionQuery(release.id).queryKey })
      toast('Replacement audio is processing. Submit the revision after processing finishes.', 'success')
    } catch {
      toast('Could not upload replacement audio.', 'error')
    }
  }

  const del = async () => {
    try {
      await apiDeleteRelease(release.id)
      queryClient.invalidateQueries({ queryKey: studioReleasesQuery().queryKey })
      toast('Release deleted', 'success')
      navigate({ to: '/studio/releases' })
    } catch {
      toast('Only drafts and in-review releases can be deleted.', 'error')
    }
  }

  if (release.status === 'draft') {
    return (
      <div className="flex flex-col gap-6">
        <Link to="/studio/releases" className="flex items-center gap-1.5 text-xs font-bold text-gray-400"><ArrowLeft size={14} /> Releases</Link>
        <section className={cn(CARD, 'flex flex-col items-center text-center gap-4 py-16')}>
          <Disc3 size={40} className="text-beatz-green" />
          <h1 className="text-2xl font-bold text-beatz-dark-bg dark:text-white">Continue editing this draft</h1>
          <p className="max-w-md text-sm text-gray-500 dark:text-gray-300">The release wizard is where you can keep uploading tracks and finish preparing this release.</p>
          <Link to="/studio/release/new/details" search={{ resume: release.id }} className="h-11 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center gap-2">Continue editing</Link>
        </section>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-2">
          <Link to="/studio/releases" className="flex items-center gap-1.5 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white w-fit"><ArrowLeft size={14} /> Releases</Link>
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-display text-beatz-dark-bg dark:text-white">{release.title}</h1>
            <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', status.cls)}>{status.label}</span>
          </div>
          <span className="text-sm text-gray-500 dark:text-gray-300">{releaseTypeLabel(release.type)} · {release.trackCount} track{release.trackCount === 1 ? '' : 's'} · {release.date}</span>
        </div>
        <div className="flex items-center gap-3">
          {live && creatorId && <button onClick={() => navigate({ to: '/artist/$artistId', params: { artistId: creatorId } })} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-sm font-bold flex items-center gap-2"><ExternalLink size={15} /> View on BeatzClik</button>}
          {!live && release.status === 'in_review' && <button onClick={del} className="h-10 px-4 rounded-full text-beatz-red font-bold text-sm flex items-center gap-2 hover:bg-beatz-red/10"><Trash2 size={15} /> Delete</button>}
        </div>
      </div>

      {pendingRevision && <div role="status" className="rounded-xl border border-beatz-blue/30 bg-beatz-blue/10 px-4 py-3 text-sm text-beatz-dark-bg dark:text-white">Your changes are being reviewed. Fans continue to see the current approved version until they are approved.</div>}
      {release.status !== 'in_review' && revision?.status === 'draft' && <div role="status" className="rounded-xl border border-[#f6c644]/30 bg-[#f6c644]/10 px-4 py-3 text-sm text-beatz-dark-bg dark:text-white">These changes are saved as a draft revision. Fans still see the approved release until you send the revision for review and a moderator approves it.</div>}
      {revision?.status === 'rejected' && revision.rejectionReason && <div role="alert" className="rounded-xl border border-beatz-red/30 bg-beatz-red/10 px-4 py-3 text-sm text-beatz-dark-bg dark:text-white"><strong>Revision needs changes:</strong> {revision.rejectionReason}</div>}
      {release.status === 'in_review' && <div className="rounded-xl border border-beatz-blue/30 bg-beatz-blue/10 px-4 py-3 text-sm text-beatz-dark-bg dark:text-white">Edits update the submission currently under review. The track list is fixed.</div>}

      <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-8 items-start">
        <div className="flex flex-col gap-4">
          <div className="relative w-full aspect-square rounded-2xl overflow-hidden flex items-center justify-center" style={{ backgroundImage: coverGradient(draft.title) }}>
            {draft.coverImage ? <img src={draft.coverImage} alt={`${draft.title} cover`} className="w-full h-full object-cover" /> : <Disc3 size={48} className="text-white/70" />}
          </div>
          <input ref={coverInput} type="file" accept="image/png,image/jpeg" className="hidden" onChange={(event) => { void uploadCover(event.currentTarget.files?.[0]); event.currentTarget.value = '' }} />
          <button disabled={!editable || busy} onClick={() => coverInput.current?.click()} className="h-10 rounded-full border border-gray-200 dark:border-white/15 text-sm font-bold flex items-center justify-center gap-2 disabled:opacity-50"><ImageIcon size={15} /> Change release cover</button>
          <div className="grid grid-cols-2 gap-3">
            <div className={cn(CARD, '!p-4 flex flex-col gap-1')}><span className={LABEL}>Streams</span><span className="text-xl font-bold">{live ? release.streams.toLocaleString() : '—'}</span></div>
            <div className={cn(CARD, '!p-4 flex flex-col gap-1')}><span className={LABEL}>Revenue</span><span className="text-xl font-bold text-beatz-green">{live ? `₵${release.revenue.toLocaleString()}` : '—'}</span></div>
          </div>
        </div>

        <section className={cn(CARD, 'flex flex-col gap-5')}>
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div><h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Release details</h2><p className="mt-1 text-xs text-gray-500">Price is recalculated from the track prices.</p></div>
            <div className="flex gap-2">
              {editable && <button onClick={save} disabled={!dirty || busy} className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-sm font-bold flex items-center gap-2 disabled:opacity-40"><Save size={14} /> Save</button>}
              {editable && release.status !== 'in_review' && <button onClick={submitRevision} disabled={busy || pendingRevision || (!dirty && revision?.status !== 'draft')} className="h-9 px-4 rounded-full bg-beatz-green text-black text-sm font-bold flex items-center gap-2 disabled:opacity-40"><Send size={14} /> Send for review</button>}
            </div>
          </div>
          {revisionQuery.isPending && <p className="text-sm text-gray-500">Loading current edit state…</p>}
          <label className="flex flex-col gap-2"><span className={LABEL}>Release title</span><input disabled={!editable || busy} className={INPUT} value={draft.title} onChange={(event) => set('title', event.target.value)} /></label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <label className="flex flex-col gap-2"><span className={LABEL}>Genre</span><input disabled={!editable || busy} className={INPUT} value={draft.genre ?? ''} onChange={(event) => set('genre', event.target.value || null)} /></label>
            <label className="flex flex-col gap-2"><span className={LABEL}>Visibility</span><select disabled={!editable || busy} className={INPUT} value={draft.visibility} onChange={(event) => set('visibility', event.target.value as 'public' | 'scheduled')}><option value="public">Public</option><option value="scheduled">Scheduled</option></select></label>
          </div>
          {draft.visibility === 'scheduled' && <label className="flex flex-col gap-2"><span className={LABEL}>Scheduled release date</span><input disabled={!editable || busy} type="datetime-local" className={INPUT} value={draft.scheduledAt?.slice(0, 16) ?? ''} onChange={(event) => set('scheduledAt', event.target.value ? new Date(event.target.value).toISOString() : null)} /></label>}
          <label className="flex flex-col gap-2"><span className={LABEL}>Description</span><textarea disabled={!editable || busy} className={cn(INPUT, 'h-28 py-3 resize-y')} value={draft.description ?? ''} onChange={(event) => set('description', event.target.value || null)} /></label>
          <div className="flex flex-col gap-3 border-t border-gray-200 dark:border-white/10 pt-4">
            <span className={LABEL}>Downloads</span>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {[{ value: true, label: 'Allow downloads' }, { value: false, label: 'Streaming only' }].map((option) => (
                <button key={String(option.value)} type="button" disabled={!editable || busy} onClick={() => set('downloadable', option.value)} className={cn('rounded-xl border p-3 text-left text-sm font-bold disabled:opacity-50', draft.downloadable === option.value ? 'border-beatz-green bg-beatz-green/10 text-beatz-green' : 'border-gray-200 dark:border-white/10 text-gray-600 dark:text-gray-300')}>{option.label}</button>
              ))}
            </div>
            <p className="text-xs text-gray-500">Changes to a live release take effect only after approval. Existing buyers keep access.</p>
          </div>
        </section>
      </div>

      <section className={cn(CARD, 'flex flex-col gap-4')} aria-labelledby="release-tracks-heading">
        <div className="flex items-center justify-between gap-4">
          <div><h2 id="release-tracks-heading" className="text-lg font-bold text-beatz-dark-bg dark:text-white">Tracks</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Edit existing tracks and reorder them. Tracks cannot be added or removed after submission.</p></div>
          <span className="shrink-0 rounded-full bg-gray-100 dark:bg-white/10 px-3 py-1 text-xs font-bold text-gray-500">{draft.tracks.length} tracks</span>
        </div>
        <ol className="divide-y divide-gray-200 dark:divide-white/10">
          {draft.tracks.map((track, index) => (
            <li key={track.trackId} className="py-4">
              <div className="flex items-center gap-3 flex-wrap">
              <span className="w-7 shrink-0 text-center text-xs font-mono text-gray-400">{String(index + 1).padStart(2, '0')}</span>
              <div className="relative w-12 h-12 rounded-lg overflow-hidden bg-gray-100 dark:bg-white/5 shrink-0">
                {track.image ? <img src={track.image} alt="" className="w-full h-full object-cover" /> : <Music2 className="m-auto mt-3 text-beatz-green" size={20} />}
              </div>
              <label className="min-w-[170px] flex-1"><span className="sr-only">Track title</span><input disabled={!editable || busy} className={cn(INPUT, 'h-10')} value={track.title} onChange={(event) => updateTrack(track.trackId, { title: event.target.value })} /></label>
              <label className="w-28"><span className="sr-only">Track price in cedis</span><input disabled={!editable || busy} type="number" min="0" step="0.01" className={cn(INPUT, 'h-10')} value={track.price} onChange={(event) => updateTrack(track.trackId, { price: Number(event.target.value) })} /></label>
              <span className="w-14 text-xs font-mono text-gray-500 inline-flex items-center gap-1"><Clock3 size={12} />{formatDuration(track.duration)}</span>
              <span className="text-[10px] text-gray-500">{track.status.replaceAll('_', ' ')}</span>
              <label className="h-9 px-3 rounded-full border border-gray-200 dark:border-white/10 text-xs font-bold flex items-center gap-1.5 cursor-pointer"><ImageIcon size={13} /> Artwork<input disabled={!editable || busy} type="file" accept="image/png,image/jpeg" className="sr-only" onChange={(event) => { void uploadTrackCover(track.trackId, event.currentTarget.files?.[0]); event.currentTarget.value = '' }} /></label>
              <label className="h-9 px-3 rounded-full border border-gray-200 dark:border-white/10 text-xs font-bold flex items-center gap-1.5 cursor-pointer"><Upload size={13} /> Replace audio<input disabled={!editable || busy} type="file" accept="audio/*" className="sr-only" onChange={(event) => { void uploadTrackAudio(track.trackId, event.currentTarget.files?.[0]); event.currentTarget.value = '' }} /></label>
              <div className="flex gap-1">
                <button disabled={!editable || index === 0 || busy} aria-label={`Move ${track.title} up`} onClick={() => moveTrack(index, -1)} className="w-8 h-8 rounded-full hover:bg-gray-100 dark:hover:bg-white/10 disabled:opacity-30"><ArrowUp size={15} className="mx-auto" /></button>
                <button disabled={!editable || index === draft.tracks.length - 1 || busy} aria-label={`Move ${track.title} down`} onClick={() => moveTrack(index, 1)} className="w-8 h-8 rounded-full hover:bg-gray-100 dark:hover:bg-white/10 disabled:opacity-30"><ArrowDown size={15} className="mx-auto" /></button>
              </div>
              </div>
              <TrackLyricsEditor releaseId={release.id} trackId={track.trackId} trackTitle={track.title} />
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}
