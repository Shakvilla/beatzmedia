import { afterEach, describe, expect, it, vi } from 'vitest'
import { platformSettingsQuery, apiSaveSettings } from './admin-settings'
import type { PlatformSettings } from '../../admin-data'

const settingsWire = {
  platformFeePct: 30,
  payoutDay: 'Friday',
  payoutMinimum: 10,
  defaultCurrency: 'GHS',
  maintenanceMode: false,
  providers: { momo: true, vodafone: true, airteltigo: true, card: true, bank: false },
  flags: { artistSignups: true, podcasts: true, events: true, tipping: true, fanMessaging: true },
}

const settings: PlatformSettings = {
  platformFeePct: 30,
  payoutDay: 'Friday',
  payoutMinimum: 10,
  defaultCurrency: 'GHS',
  maintenanceMode: false,
  providers: { momo: true, vodafone: true, airteltigo: true, card: true, bank: false },
  flags: { artistSignups: true, podcasts: true, events: true, tipping: true, fanMessaging: true },
}

function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json,
    text: async () => JSON.stringify(json),
  } as Response)
}

afterEach(() => vi.restoreAllMocks())

describe('admin-settings queries', () => {
  it('platformSettingsQuery hits /v1/admin/settings', async () => {
    const f = mockFetch(200, settingsWire)
    vi.stubGlobal('fetch', f)
    const result = await platformSettingsQuery().queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/settings', expect.objectContaining({ method: 'GET' }))
    expect(result.platformFeePct).toBe(30)
    expect(platformSettingsQuery().queryKey).toEqual(['admin', 'settings'])
  })

  it('apiSaveSettings PUTs the COMPLETE object, including providers and flags', async () => {
    const f = mockFetch(200, settingsWire)
    vi.stubGlobal('fetch', f)
    await apiSaveSettings(settings)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/settings')
    const [, opts] = f.mock.calls[0]
    expect(opts.method).toBe('PUT')
    const body = JSON.parse(opts.body)
    expect(Object.keys(body).sort()).toEqual(
      ['defaultCurrency', 'flags', 'maintenanceMode', 'payoutDay', 'payoutMinimum', 'platformFeePct', 'providers'].sort(),
    )
    expect(body.providers).toEqual(settings.providers)
    expect(body.flags).toEqual(settings.flags)
  })
})
