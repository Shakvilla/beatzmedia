/**
 * Platform admin mock data.
 *
 * Powers the standalone admin console (admin.beatzclik.com). Self-contained
 * mock seeds so the console renders without a backend. Swap `getAdminOverview`
 * etc. for real queries later.
 */

export interface AdminUser {
  name: string
  role: string
  initials: string
}

export const adminUser: AdminUser = {
  name: 'Yaa',
  role: 'Super-admin',
  initials: 'AD',
}

export type AdminRange = '24h' | '7d' | '30d'

export const ADMIN_RANGES: { key: AdminRange; label: string }[] = [
  { key: '24h', label: 'last 24 hours' },
  { key: '7d', label: 'last 7 days' },
  { key: '30d', label: 'last 30 days' },
]

export interface AttentionItem {
  id: string
  label: string
  sub: string
  to: string
}

export interface RevenueArtist { name: string; revenue: number }
export interface PayMethod { name: string; value: number }

export interface AdminOverview {
  rangeLabel: string
  kpis: {
    activeUsers: number
    streams: number
    gmv: number
    newArtists: number
    deltas: { users: number; streams: number; gmv: number }
  }
  gmvByDay: number[]
  needsAttention: AttentionItem[]
  topArtists: RevenueArtist[]
  paymentMethods: PayMethod[]
}

const RANGE_META: Record<AdminRange, { label: string; bars: number; deltas: { users: number; streams: number; gmv: number } }> = {
  '24h': { label: 'last 24 hours', bars: 24, deltas: { users: 12, streams: 8, gmv: 18 } },
  '7d': { label: 'last 7 days', bars: 22, deltas: { users: 22, streams: 15, gmv: 34 } },
  '30d': { label: 'last 30 days', bars: 30, deltas: { users: 64, streams: 41, gmv: 96 } },
}

/* --------------------------------- Users --------------------------------- */

export type UserRole = 'fan' | 'artist'
export type UserStatus = 'active' | 'pending' | 'suspended'

export interface AdminUserRow {
  id: string
  name: string
  initial: string
  email: string
  role: UserRole
  verified: boolean
  joined: string
  lastActive: string
  status: UserStatus
}

export const USER_COUNTS = { all: 48210, fans: 46880, artists: 1260, verified: 84, suspended: 32 }

export interface UserActivity { id: string; text: string; time: string }
export interface UserOrder { id: string; item: string; amount: number; date: string }
export interface UserDevice { id: string; device: string; location: string; lastActive: string; current: boolean }
export interface UserActionLog { id: string; action: string; by: string; time: string }

export interface UserDetail {
  activity: UserActivity[]
  orders: UserOrder[]
  devices: UserDevice[]
}

/** Generic profile detail used to populate a user's admin page. */
export function getUserDetail(): UserDetail {
  return {
    activity: [
      { id: 'ac1', text: 'Bought “Soja” · ₵2.50', time: '2h ago' },
      { id: 'ac2', text: 'Followed Black Sherif', time: '1d ago' },
      { id: 'ac3', text: 'Created playlist “Late Night”', time: '3d ago' },
      { id: 'ac4', text: 'Tipped @kwesi_ · ₵5.00', time: '1w ago' },
      { id: 'ac5', text: 'Signed in · MTN data', time: '1w ago' },
    ],
    orders: [
      { id: 'o1', item: 'Soja · Black Sherif', amount: 2.5, date: 'May 02' },
      { id: 'o2', item: 'The Villain I Never Was', amount: 18.99, date: 'Apr 27' },
      { id: 'o3', item: 'Kwaku the Traveller', amount: 2.5, date: 'Apr 12' },
    ],
    devices: [
      { id: 'dv1', device: 'iPhone 15 · BeatzClik app', location: 'Accra, GH', lastActive: 'Active now', current: true },
      { id: 'dv2', device: 'Chrome · Windows', location: 'Kumasi, GH', lastActive: '2 days ago', current: false },
    ],
  }
}

export function getAdminUsers(): AdminUserRow[] {
  return [
    { id: 'u-kojo', name: 'Kojo Asante', initial: 'K', email: 'kojo@gmail.com', role: 'fan', verified: false, joined: 'Mar 2024', lastActive: '2m ago', status: 'active' },
    { id: 'u-blacko', name: 'Black Sherif', initial: 'B', email: 'team@blacksherif.com', role: 'artist', verified: true, joined: 'Jan 2023', lastActive: '1h ago', status: 'active' },
    { id: 'u-ama', name: 'Ama Boateng', initial: 'A', email: 'ama_b@gmail.com', role: 'fan', verified: false, joined: 'Sep 2024', lastActive: 'now', status: 'active' },
    { id: 'u-djkojo', name: 'DJ Kojo', initial: 'D', email: 'dj@mixtape.gh', role: 'artist', verified: false, joined: 'Feb 2025', lastActive: '3d', status: 'pending' },
    { id: 'u-yaw', name: 'Yaw Mensah', initial: 'Y', email: 'yaw@gmail.com', role: 'fan', verified: false, joined: 'Apr 2025', lastActive: '1w', status: 'suspended' },
    { id: 'u-nana', name: 'Nana Akua', initial: 'N', email: 'akua@gmail.com', role: 'fan', verified: false, joined: 'Jul 2024', lastActive: '12m', status: 'active' },
    { id: 'u-osei', name: 'Osei Beats', initial: 'O', email: 'osei.b@producer.gh', role: 'artist', verified: false, joined: 'Nov 2024', lastActive: '1d', status: 'active' },
    { id: 'u-esi', name: 'Esi Owusu', initial: 'E', email: 'esi@gmail.com', role: 'fan', verified: false, joined: 'Dec 2024', lastActive: '4h', status: 'active' },
    { id: 'u-abena', name: 'Abena Pokua', initial: 'A', email: 'abena@gmail.com', role: 'fan', verified: false, joined: 'Jan 2025', lastActive: '8m', status: 'active' },
    { id: 'u-kofi', name: 'Kofi Mensah', initial: 'K', email: 'kofi.m@gmail.com', role: 'fan', verified: false, joined: 'Feb 2024', lastActive: '2d', status: 'active' },
    { id: 'u-sark', name: 'Sarkodie', initial: 'S', email: 'team@sarkodie.com', role: 'artist', verified: true, joined: 'Jun 2022', lastActive: '5h', status: 'active' },
    { id: 'u-efya', name: 'Efya', initial: 'E', email: 'mgmt@efya.com', role: 'artist', verified: true, joined: 'Aug 2022', lastActive: '1d', status: 'active' },
    { id: 'u-adwoa', name: 'Adwoa Safo', initial: 'A', email: 'adwoa.s@gmail.com', role: 'fan', verified: false, joined: 'Mar 2025', lastActive: '3h', status: 'active' },
    { id: 'u-fiifi', name: 'Fiifi Coleman', initial: 'F', email: 'fiifi@gmail.com', role: 'fan', verified: false, joined: 'Oct 2024', lastActive: '6d', status: 'suspended' },
    { id: 'u-akua2', name: 'Akua Donkor', initial: 'A', email: 'akua.d@gmail.com', role: 'fan', verified: false, joined: 'Nov 2024', lastActive: '20m', status: 'active' },
    { id: 'u-joey', name: 'Joey B', initial: 'J', email: 'team@joeyb.com', role: 'artist', verified: true, joined: 'Apr 2023', lastActive: '12h', status: 'active' },
    { id: 'u- debo', name: 'Kojo Debrah', initial: 'K', email: 'kdebrah@gmail.com', role: 'fan', verified: false, joined: 'Jan 2024', lastActive: '2w', status: 'active' },
    { id: 'u- as', name: 'Ato Sarpong', initial: 'A', email: 'ato@beatmaker.gh', role: 'artist', verified: false, joined: 'May 2025', lastActive: '4d', status: 'pending' },
  ]
}

/* ------------------------------ Compliance ------------------------------- */

export type ComplianceType = 'DSAR-export' | 'DSAR-delete' | 'Takedown' | 'Tax'
export type ComplianceStatus = 'new' | 'in_progress' | 'completed' | 'overdue'

export interface ComplianceRequest {
  id: string
  type: ComplianceType
  subject: string
  detail: string
  due: string
  status: ComplianceStatus
}

export function getCompliance(): ComplianceRequest[] {
  return [
    { id: 'co1', type: 'DSAR-export', subject: '@ama_b', detail: 'Personal data export request', due: 'in 12 days', status: 'new' },
    { id: 'co2', type: 'DSAR-delete', subject: 'Yaw Mensah', detail: 'Account & data deletion request', due: 'in 5 days', status: 'in_progress' },
    { id: 'co3', type: 'Takedown', subject: '“Free Money” · @kwabz', detail: 'DMCA notice · Empire Records', due: 'overdue 1 day', status: 'overdue' },
    { id: 'co4', type: 'Tax', subject: '2025 annual statements', detail: '1,140 artist tax statements', due: 'in 30 days', status: 'new' },
    { id: 'co5', type: 'Takedown', subject: 'Album art · “Vibes”', detail: 'Trademark complaint', due: 'in 3 days', status: 'in_progress' },
    { id: 'co6', type: 'DSAR-export', subject: '@kwesi_', detail: 'Personal data export request', due: 'completed', status: 'completed' },
  ]
}

/* ----------------------------- Trust & Safety ---------------------------- */

export type RiskLevel = 'high' | 'med' | 'low'
export type RiskStatus = 'open' | 'cleared' | 'banned'

export interface RiskSignal {
  id: string
  subject: string
  type: string
  detail: string
  level: RiskLevel
  time: string
  status: RiskStatus
}

export const RISK_KPIS = { chargebackRate: '0.4%', suspiciousSignups: 23, fraudFlags: 6, botStreams: '1.2%' }

export function getRiskSignals(): RiskSignal[] {
  return [
    { id: 'r1', subject: '@new_artist', type: 'Payment fraud', detail: '3 failed payouts · KYC mismatch', level: 'high', time: '20m ago', status: 'open' },
    { id: 'r2', subject: 'card ···4421', type: 'Chargeback', detail: '4 disputes in 30 days', level: 'high', time: '1h ago', status: 'open' },
    { id: 'r3', subject: '@anon', type: 'Suspicious signup', detail: '14 accounts · same device fingerprint', level: 'med', time: '2h ago', status: 'open' },
    { id: 'r4', subject: '“untitled-001”', type: 'Bot streams', detail: '92% of plays from one ASN', level: 'med', time: '4h ago', status: 'open' },
    { id: 'r5', subject: '@yaw_g', type: 'Account takeover', detail: 'Login from new country · password reset', level: 'high', time: '6h ago', status: 'open' },
    { id: 'r6', subject: '“Crank It Up”', type: 'Stream manipulation', detail: 'Streams +400% overnight', level: 'low', time: '1d ago', status: 'open' },
  ]
}

/* ------------------------------- Support --------------------------------- */

export type TicketStatus = 'open' | 'pending' | 'resolved'
export type TicketPriority = 'high' | 'normal' | 'low'

export interface SupportMessage { id: string; from: 'user' | 'agent'; author: string; text: string; time: string }

export interface SupportTicket {
  id: string
  subject: string
  requester: string
  channel: string
  priority: TicketPriority
  status: TicketStatus
  age: string
  messages: SupportMessage[]
}

export function getSupportTickets(): SupportTicket[] {
  return [
    {
      id: 't1', subject: 'Payout not received', requester: 'Black Sherif', channel: 'email', priority: 'high', status: 'open', age: '2h',
      messages: [
        { id: 'm1', from: 'user', author: 'Black Sherif', text: 'My Friday payout of ₵42,180 hasn’t arrived on MTN MoMo. Can you check?', time: '2h ago' },
        { id: 'm2', from: 'agent', author: 'Yaa (Support)', text: 'Looking into it now — I can see it’s queued. Confirming with the provider.', time: '1h ago' },
      ],
    },
    { id: 't2', subject: 'Can’t upload WAV', requester: 'DJ Kojo', channel: 'in-app', priority: 'normal', status: 'open', age: '5h',
      messages: [{ id: 'm1', from: 'user', author: 'DJ Kojo', text: 'Upload fails at 90% every time for my WAV files.', time: '5h ago' }] },
    { id: 't3', subject: 'Refund for album', requester: '@ama_b', channel: 'email', priority: 'normal', status: 'pending', age: '1d',
      messages: [{ id: 'm1', from: 'user', author: '@ama_b', text: 'I bought an album that won’t play. I’d like a refund.', time: '1d ago' }] },
    { id: 't4', subject: 'Double charged', requester: '@kwesi_', channel: 'email', priority: 'high', status: 'open', age: '30m',
      messages: [{ id: 'm1', from: 'user', author: '@kwesi_', text: 'I was charged twice for the same track.', time: '30m ago' }] },
    { id: 't5', subject: 'Verify my artist account', requester: 'Osei Beats', channel: 'in-app', priority: 'low', status: 'open', age: '2d',
      messages: [{ id: 'm1', from: 'user', author: 'Osei Beats', text: 'How long does verification take? Submitted 3 days ago.', time: '2d ago' }] },
    { id: 't6', subject: 'Account suspended?', requester: 'Yaw Mensah', channel: 'email', priority: 'normal', status: 'resolved', age: '3d',
      messages: [
        { id: 'm1', from: 'user', author: 'Yaw Mensah', text: 'Why is my account suspended?', time: '3d ago' },
        { id: 'm2', from: 'agent', author: 'Yaa (Support)', text: 'It was flagged for chargebacks; reinstated after review. You’re all set.', time: '3d ago' },
      ] },
  ]
}

/* ---------------------------- Settings + RBAC ---------------------------- */

export type AdminRole = 'Super-admin' | 'Finance' | 'Moderator' | 'Editor' | 'Support'

export const ADMIN_ROLES: { role: AdminRole; scope: string }[] = [
  { role: 'Super-admin', scope: 'Full access to every section and settings' },
  { role: 'Finance', scope: 'Payouts, ledger and disputes' },
  { role: 'Moderator', scope: 'Moderation queue and takedowns' },
  { role: 'Editor', scope: 'Editorial, featured slots and playlists' },
  { role: 'Support', scope: 'User lookup, read-only elsewhere' },
]

export interface AdminMember { id: string; name: string; email: string; role: AdminRole; lastActive: string }

export function getAdminTeam(): AdminMember[] {
  return [
    { id: 'a-yaa', name: 'Yaa Mensima', email: 'yaa@beatzclik.com', role: 'Super-admin', lastActive: 'now' },
    { id: 'a-kofi', name: 'Kofi Annor', email: 'kofi@beatzclik.com', role: 'Finance', lastActive: '2h ago' },
    { id: 'a-adwoa', name: 'Adwoa Smart', email: 'adwoa@beatzclik.com', role: 'Moderator', lastActive: '1d ago' },
    { id: 'a-kwame', name: 'Kwame DJ', email: 'kwame@beatzclik.com', role: 'Editor', lastActive: '3d ago' },
  ]
}

export interface PlatformSettings {
  platformFeePct: number
  payoutDay: string
  payoutMinimum: number
  defaultCurrency: string
  maintenanceMode: boolean
  providers: { momo: boolean; vodafone: boolean; airteltigo: boolean; card: boolean; bank: boolean }
  flags: { artistSignups: boolean; podcasts: boolean; events: boolean; tipping: boolean; fanMessaging: boolean }
}

export function getPlatformSettings(): PlatformSettings {
  return {
    platformFeePct: 30,
    payoutDay: 'Friday',
    payoutMinimum: 10,
    defaultCurrency: 'GHS',
    maintenanceMode: false,
    providers: { momo: true, vodafone: true, airteltigo: true, card: true, bank: true },
    flags: { artistSignups: true, podcasts: true, events: true, tipping: true, fanMessaging: false },
  }
}

/* -------------------------------- Audit log ------------------------------ */

export type AuditType = 'user' | 'catalog' | 'finance' | 'moderation' | 'settings' | 'editorial'

export interface AuditEntry {
  id: string
  actor: string
  action: string
  target: string
  type: AuditType
  time: string
}

export function getAuditLog(): AuditEntry[] {
  return [
    { id: 'al1', actor: 'Yaa Mensima', action: 'Suspended user', target: 'Yaw Mensah', type: 'user', time: '12m ago' },
    { id: 'al2', actor: 'Kofi Annor', action: 'Ran weekly payout', target: '1,140 artists · ₵518k', type: 'finance', time: '1h ago' },
    { id: 'al3', actor: 'Adwoa Smart', action: 'Removed content', target: 'Comment thread on @blacko', type: 'moderation', time: '2h ago' },
    { id: 'al4', actor: 'Yaa Mensima', action: 'Verified artist', target: 'Black Sherif', type: 'user', time: '3h ago' },
    { id: 'al5', actor: 'Kwame DJ', action: 'Reordered featured', target: 'Home featured · Ghana', type: 'editorial', time: '5h ago' },
    { id: 'al6', actor: 'Adwoa Smart', action: 'Flagged release', target: 'Highlife Forever', type: 'catalog', time: '6h ago' },
    { id: 'al7', actor: 'Yaa Mensima', action: 'Changed platform fee', target: '28% → 30%', type: 'settings', time: '1d ago' },
    { id: 'al8', actor: 'Kofi Annor', action: 'Resolved dispute', target: 'Chargeback · BZ-2026-0481', type: 'finance', time: '1d ago' },
    { id: 'al9', actor: 'Yaa Mensima', action: 'Approved release', target: 'The Villain I Never Was', type: 'catalog', time: '2d ago' },
    { id: 'al10', actor: 'Adwoa Smart', action: 'Took down release', target: 'Free Money · @kwabz', type: 'catalog', time: '2d ago' },
    { id: 'al11', actor: 'Yaa Mensima', action: 'Invited admin', target: 'kwame@beatzclik.com · Editor', type: 'settings', time: '3d ago' },
    { id: 'al12', actor: 'Kwame DJ', action: 'Scheduled push', target: 'Friday drops · 8 new', type: 'editorial', time: '4d ago' },
  ]
}

/* --------------------------------- Health -------------------------------- */

export interface HealthMetric { label: string; value: string; sub: string }
export interface Incident { id: string; title: string; date: string; status: 'resolved' | 'open' }

export interface Health {
  status: 'normal' | 'degraded'
  metrics: HealthMetric[]
  listeners: number[]
  incidents: Incident[]
}

export function getHealth(): Health {
  return {
    status: 'normal',
    metrics: [
      { label: 'API p95', value: '142ms', sub: 'budget 200ms' },
      { label: 'Streaming uptime', value: '99.98%', sub: 'last 30d' },
      { label: 'MoMo gateway', value: 'OK', sub: '2 retries · 0 failed' },
      { label: 'CDN errors', value: '0.02%', sub: 'budget 0.1%' },
    ],
    listeners: [0.42, 0.40, 0.38, 0.36, 0.35, 0.34, 0.36, 0.40, 0.46, 0.52, 0.58, 0.62, 0.66, 0.70, 0.73, 0.77, 0.82, 0.86, 0.90, 0.94, 0.97, 1.0, 0.98, 0.88, 0.62],
    incidents: [
      { id: 'i1', title: 'MoMo MTN · 4m delay', date: 'Apr 24', status: 'resolved' },
      { id: 'i2', title: 'CDN edge ACC · degraded', date: 'Apr 18', status: 'resolved' },
      { id: 'i3', title: 'Search slow · 8% errors', date: 'Apr 02', status: 'resolved' },
    ],
  }
}

/* ------------------------------- Editorial ------------------------------- */

export interface FeaturedSlot {
  id: string
  title: string
  note: string
  sponsored?: boolean
}

export interface PushItem {
  id: string
  day: string
  time: string
  title: string
  audience: string
}

export interface CuratedPlaylist { id: string; name: string }

export interface Editorial {
  featured: FeaturedSlot[]
  pushSchedule: PushItem[]
  playlists: CuratedPlaylist[]
}

export function getEditorial(): Editorial {
  return {
    featured: [
      { id: 'f1', title: 'Trending in Ghana', note: 'updated daily' },
      { id: 'f2', title: 'Made in Ghana 2026', note: 'manual · 64 tracks' },
      { id: 'f3', title: 'Iron Boy · Black Sherif', note: 'sponsored slot · 24h', sponsored: true },
      { id: 'f4', title: 'Hiplife Forever', note: 'manual · 102 tracks' },
      { id: 'f5', title: 'Sunday Praise', note: 'auto · gospel' },
    ],
    pushSchedule: [
      { id: 'ps1', day: 'Mon', time: '6PM', title: 'Iron Boy out now', audience: '1.2M' },
      { id: 'ps2', day: 'Wed', time: '12PM', title: 'Wednesday Wrap-up · top 10', audience: 'All users' },
      { id: 'ps3', day: 'Fri', time: '9AM', title: 'Friday drops · 8 new', audience: '1.4M' },
      { id: 'ps4', day: 'Sat', time: '8PM', title: 'Live: Sarkodie acoustic', audience: 'Followers · 412K' },
    ],
    playlists: [
      { id: 'pl1', name: 'Made in Ghana' },
      { id: 'pl2', name: 'Hiplife Hour' },
      { id: 'pl3', name: 'Drill Wave' },
      { id: 'pl4', name: 'Sunday Praise' },
      { id: 'pl5', name: 'Highlife Forever' },
      { id: 'pl6', name: 'Workout 2026' },
    ],
  }
}

/* -------------------------------- Finance -------------------------------- */

export type PayoutMethodLabel = string
export type PendingStatus = 'ready' | 'kyc_pending'

export interface PendingPayout {
  id: string
  artist: string
  amount: number
  method: PayoutMethodLabel
  status: PendingStatus
}

export interface ProviderMix { name: string; value: number }

export interface Dispute {
  id: string
  kind: string
  subject: string
  detail: string
  amount?: number
  opened?: string
}

export interface TimelineEntry { id: string; text: string; time: string }

export function getDisputeTimeline(): TimelineEntry[] {
  return [
    { id: 't1', text: 'Dispute opened by fan', time: '3 days ago' },
    { id: 't2', text: 'Auto-flagged · payment provider notice', time: '3 days ago' },
    { id: 't3', text: 'Assigned to Finance · Kofi Annor', time: '2 days ago' },
    { id: 't4', text: 'Artist asked for delivery proof', time: '1 day ago' },
  ]
}

export type LedgerType = 'Sale' | 'Royalty' | 'Tip' | 'Payout' | 'Refund' | 'Fee'
export interface LedgerTxn { id: string; date: string; type: LedgerType; party: string; ref: string; amount: number }

export function getLedger(): LedgerTxn[] {
  return [
    { id: 'lg1', date: 'May 03', type: 'Sale', party: 'Soja · Black Sherif', ref: 'BZ-2026-0501', amount: 2.5 },
    { id: 'lg2', date: 'May 03', type: 'Fee', party: 'Platform fee · 30%', ref: 'BZ-2026-0501', amount: 0.75 },
    { id: 'lg3', date: 'May 02', type: 'Royalty', party: 'Stream royalties · 24h', ref: 'RY-0502', amount: 620.4 },
    { id: 'lg4', date: 'May 02', type: 'Tip', party: 'Tip · @ama_b → Black Sherif', ref: 'TP-2291', amount: 20 },
    { id: 'lg5', date: 'May 01', type: 'Payout', party: 'Payout · Black Sherif · MTN', ref: 'PO-1180', amount: -42_180 },
    { id: 'lg6', date: 'Apr 30', type: 'Sale', party: 'The Villain · Black Sherif', ref: 'BZ-2026-0498', amount: 18.99 },
    { id: 'lg7', date: 'Apr 29', type: 'Refund', party: 'Refund · @ama_b', ref: 'RF-0044', amount: -18.99 },
    { id: 'lg8', date: 'Apr 28', type: 'Payout', party: 'Payout · Stonebwoy · GCB', ref: 'PO-1179', amount: -38_920 },
    { id: 'lg9', date: 'Apr 28', type: 'Tip', party: 'Tip · @kwesi_ → Sarkodie', ref: 'TP-2287', amount: 5 },
    { id: 'lg10', date: 'Apr 27', type: 'Sale', party: 'Kwaku the Traveller', ref: 'BZ-2026-0490', amount: 2.5 },
    { id: 'lg11', date: 'Apr 27', type: 'Fee', party: 'Platform fee · 30%', ref: 'BZ-2026-0490', amount: 0.75 },
    { id: 'lg12', date: 'Apr 26', type: 'Refund', party: 'Chargeback · card', ref: 'BZ-2026-0481', amount: -0.18 * 1000 },
  ]
}

export interface Finance {
  kpis: { gmvMtd: number; gmvDelta: number; platformFee: number; feeTakePct: number; payoutsDue: number; payoutsArtists: number; momoFloat: number }
  pendingPayouts: PendingPayout[]
  providerMix: ProviderMix[]
  disputes: Dispute[]
}

export function getFinance(): Finance {
  return {
    kpis: { gmvMtd: 842_000, gmvDelta: 18, platformFee: 252_000, feeTakePct: 30, payoutsDue: 518_000, payoutsArtists: 1_140, momoFloat: 1_400_000 },
    pendingPayouts: [
      { id: 'p1', artist: 'Black Sherif', amount: 42_180, method: 'MoMo · MTN', status: 'ready' },
      { id: 'p2', artist: 'Stonebwoy', amount: 38_920, method: 'Bank · GCB', status: 'ready' },
      { id: 'p3', artist: 'Sarkodie', amount: 31_440, method: 'MoMo · Vodafone', status: 'ready' },
      { id: 'p4', artist: 'King Promise', amount: 24_180, method: 'MoMo · MTN', status: 'kyc_pending' },
      { id: 'p5', artist: 'Joey B', amount: 18_640, method: 'MoMo · MTN', status: 'ready' },
    ],
    providerMix: [
      { name: 'MTN', value: 100 },
      { name: 'VOD.', value: 38 },
      { name: 'AT', value: 22 },
      { name: 'Card', value: 12 },
    ],
    disputes: [
      { id: 'd1', kind: 'Failed payout', subject: '@new_artist', detail: 'KYC mismatch', amount: 8_400, opened: 'Apr 22' },
      { id: 'd2', kind: 'Refund request', subject: '@ama_b', detail: 'Album not delivered', amount: 18.99, opened: 'Apr 24' },
      { id: 'd3', kind: 'Chargeback', subject: 'order BZ-2026-0481', detail: 'Card · ₵180', amount: 180, opened: 'Apr 25' },
    ],
  }
}

/* -------------------------------- Catalog -------------------------------- */

export type CatalogType = 'Album' | 'Single' | 'EP' | 'Compilation' | 'Mixtape'
export type CatalogStatus = 'pending' | 'flagged' | 'published' | 'takedown'

export interface CatalogItem {
  id: string
  title: string
  note?: string
  artist: string
  type: CatalogType
  tracks: number
  status: CatalogStatus
}

export const CATALOG_SUMMARY = { artists: 1260, albums: 18420, tracks: 142800 }
export const CATALOG_COUNTS = { pending: 24, published: 18396, takedown: 8 }

export function getCatalog(): CatalogItem[] {
  return [
    { id: 'c1', title: 'Iron Boy', note: 'submitted 2h ago', artist: 'Black Sherif', type: 'Album', tracks: 14, status: 'pending' },
    { id: 'c2', title: 'Last Last', note: '5h', artist: 'Burna Boy', type: 'Single', tracks: 1, status: 'pending' },
    { id: 'c3', title: 'Joy is Coming', note: '1d', artist: 'Fido', type: 'Single', tracks: 1, status: 'pending' },
    { id: 'c4', title: 'My Story EP', note: '12h', artist: 'King Promise', type: 'EP', tracks: 5, status: 'pending' },
    { id: 'c5', title: 'Highlife Forever', note: 'metadata mismatch', artist: 'Various', type: 'Compilation', tracks: 24, status: 'flagged' },
    { id: 'c6', title: 'Untitled-001', note: 'duplicate ISRC', artist: '@anon', type: 'Single', tracks: 1, status: 'flagged' },
    { id: 'c7', title: 'Sunday Praise Vol 8', note: '18h', artist: 'Joe Mettle', type: 'Album', tracks: 11, status: 'pending' },
    { id: 'c8', title: 'Trotro Beats', note: '1d', artist: 'DJ Kojo', type: 'Mixtape', tracks: 18, status: 'pending' },
    { id: 'c9', title: 'The Villain I Never Was', artist: 'Black Sherif', type: 'Album', tracks: 14, status: 'published' },
    { id: 'c10', title: 'Soja', artist: 'Black Sherif', type: 'Single', tracks: 1, status: 'published' },
    { id: 'c11', title: 'Free Money', note: 'copyright claim', artist: '@kwabz', type: 'Single', tracks: 1, status: 'takedown' },
  ]
}

/* ------------------------------ Moderation ------------------------------ */

export type ModReason = 'Copyright' | 'Hate speech' | 'Sexual content' | 'Spam' | 'Impersonation'
export type ModSeverity = 'high' | 'med' | 'low'
export type ModStatus = 'open' | 'in_review' | 'resolved'

export interface ModerationItem {
  id: string
  item: string
  reporter: string
  reason: ModReason
  age: string
  severity: ModSeverity
  status: ModStatus
}

export const MOD_TYPES: ModReason[] = ['Copyright', 'Hate speech', 'Sexual content', 'Spam', 'Impersonation']
export const MOD_SLA_HOURS = 6
export const MOD_ESCALATED = 3

export function getModerationQueue(): ModerationItem[] {
  return [
    { id: 'm1', item: 'Track · “Free Money” by @kwabz', reporter: '@dj_kojo', reason: 'Copyright', age: '6h', severity: 'high', status: 'open' },
    { id: 'm2', item: 'Comment on “Last Last” thread', reporter: '@ama_b', reason: 'Hate speech', age: '2h', severity: 'high', status: 'open' },
    { id: 'm3', item: 'Artist profile @sark_official', reporter: 'auto-flag', reason: 'Impersonation', age: '4h', severity: 'med', status: 'open' },
    { id: 'm4', item: 'Album art · “Vibes” cover', reporter: '@yaa_g', reason: 'Sexual content', age: '12h', severity: 'med', status: 'open' },
    { id: 'm5', item: 'Track · “untitled-001”', reporter: 'auto-flag', reason: 'Spam', age: '1d', severity: 'low', status: 'open' },
    { id: 'm6', item: 'Playlist · “Crank It Up”', reporter: '@osei.b', reason: 'Copyright', age: '18h', severity: 'med', status: 'open' },
    { id: 'm7', item: 'Comment thread on @blacko', reporter: '5 reports', reason: 'Hate speech', age: '30m', severity: 'high', status: 'open' },
    { id: 'm8', item: 'Live stream · @new_artist', reporter: 'auto-flag', reason: 'Sexual content', age: '5m', severity: 'high', status: 'open' },
  ]
}

function rng(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function buildBars(count: number, seed: number): number[] {
  const r = rng(seed)
  const out: number[] = []
  let v = 0.45
  for (let i = 0; i < count; i++) {
    v += 0.5 / count + (r() - 0.42) * 0.12
    out.push(Math.max(0.15, Math.min(1, v)))
  }
  return out
}

export function getAdminOverview(range: AdminRange): AdminOverview {
  const m = RANGE_META[range]
  return {
    rangeLabel: m.label,
    kpis: {
      activeUsers: 48_210,
      streams: 2_400_000,
      gmv: 142_000,
      newArtists: 124,
      deltas: m.deltas,
    },
    gmvByDay: buildBars(m.bars, m.bars * 131),
    needsAttention: [
      { id: 'a1', label: '14 takedown requests', sub: 'Review queue · oldest 6h', to: '/admin/moderation' },
      { id: 'a2', label: '3 payout disputes', sub: 'Artist support escalation', to: '/admin/finance' },
      { id: 'a3', label: '8 verification requests', sub: 'New artist applications', to: '/admin/users' },
      { id: 'a4', label: 'Catalog gap: Highlife', sub: 'Genre under-represented', to: '/admin/catalog' },
    ],
    topArtists: [
      { name: 'Black Sherif', revenue: 42_000 },
      { name: 'Stonebwoy', revenue: 38_000 },
      { name: 'Sarkodie', revenue: 31_000 },
      { name: 'King Promise', revenue: 24_000 },
    ],
    paymentMethods: [
      { name: 'MTN MoMo', value: 82_000 },
      { name: 'Vodafone Cash', value: 31_000 },
      { name: 'AirtelTigo', value: 17_000 },
      { name: 'Card', value: 12_000 },
    ],
  }
}
