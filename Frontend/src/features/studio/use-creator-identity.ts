import { useQuery } from '@tanstack/react-query'
import { artistQuery } from '../../lib/api/queries/catalog'
import { studioProfileQuery } from '../../lib/api/queries/studio'
import { useAuth } from '../auth/auth-context'
import { initialsOf } from '../../utils/initials'

export { initialsOf } from '../../utils/initials'

export interface CreatorIdentity {
  /**
   * The signed-in creator's artist id, or null when signed out. This is the
   * account id: every Studio endpoint derives its ArtistId straight from the
   * JWT subject (`StudioProfileResource#artistId` → `new ArtistId(jwt.getSubject())`),
   * so the account id is the id the public artist page expects too.
   */
  id: string | null
  /** Studio profile display name, falling back to the account name. May be ''. */
  name: string
  initials: string
  /** True only when the creator's public artist profile actually says so. */
  verified: boolean
  avatar: string | null
}

/**
 * The signed-in creator's own identity for Studio screens.
 *
 * Replaces the `studioArtist` mock constant, which hard-coded Black Sherif and
 * so sent every creator to his public page and showed everyone a Verified badge.
 *
 * `verified` comes from the public artist profile because neither the Studio
 * profile contract nor the account carries it. That request 404s for a creator
 * with no public profile yet, which is why it is read as `=== true`: an absent
 * or failed profile means "not verified", never a fabricated badge.
 */
export function useCreatorIdentity(): CreatorIdentity {
  const { account } = useAuth()
  const id = account?.id ?? null
  const { data: profile } = useQuery(studioProfileQuery())
  const { data: artist } = useQuery({ ...artistQuery(id ?? ''), enabled: id !== null })

  const name = profile?.displayName.trim() || account?.name?.trim() || ''
  return {
    id,
    name,
    initials: initialsOf(name),
    verified: artist?.verified === true,
    avatar: profile?.avatar ?? null,
  }
}
