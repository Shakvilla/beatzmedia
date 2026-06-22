import { Link, useNavigate } from "@tanstack/react-router"
import { useEffect, useState, type ReactNode } from "react"
import { Home, Search as SearchIcon, Library, Mic, Download, Store, CalendarDays, ChevronsLeft, ChevronsRight, Plus, Heart, ListMusic, Disc3, type LucideIcon } from "lucide-react"
import { useTheme } from "../theme-provider"
import { cn } from "../../utils/cn"
import { SidebarTooltip } from "../ui/sidebar-tooltip"
import { useCollection } from "../../features/collection/collection-context"
import { CreatePlaylistModal } from "../../features/collection/components/create-playlist-modal"
import logoFull from "../../assets/logos/logo-with-name-flex.svg"
import logoFullLight from "../../assets/logos/logo-with-name-flex-dark-mode.svg"
import logoMark from "../../assets/logos/logo-without-name.svg"
import logoMarkLight from "../../assets/logos/logo-without-name-dark-mode.svg"

const STORAGE_KEY = "beatz-sidebar-collapsed"

const NAV: { to: string; icon: LucideIcon; label: string }[] = [
  { to: "/", icon: Home, label: "Home" },
  { to: "/search", icon: SearchIcon, label: "Search" },
  { to: "/library", icon: Library, label: "Library" },
  { to: "/store", icon: Store, label: "Store" },
  { to: "/podcasts", icon: Mic, label: "Podcasts" },
  { to: "/events", icon: CalendarDays, label: "Events" },
]

export function Sidebar() {
  const { resolvedTheme } = useTheme()
  const isDark = resolvedTheme === "dark"
  const navigate = useNavigate()
  const { userPlaylists } = useCollection()
  const [createOpen, setCreateOpen] = useState(false)
  const [collapsed, setCollapsed] = useState<boolean>(
    () => typeof window !== "undefined" && localStorage.getItem(STORAGE_KEY) === "1",
  )

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, collapsed ? "1" : "0")
  }, [collapsed])

  return (
    <aside
      className={cn(
        "bg-beatz-light-surface dark:bg-black hidden md:flex flex-col gap-6 border-r border-gray-200 dark:border-white/5 flex-shrink-0 z-30 transition-[width] duration-300",
        collapsed ? "w-20 p-3" : "w-64 p-6",
      )}
    >
      {/* Logo + toggle */}
      <div className={cn("flex items-center gap-2", collapsed ? "flex-col" : "justify-between px-2")}>
        <Link to="/" className="flex items-center hover:opacity-80 transition-opacity">
          <img
            src={collapsed ? (isDark ? logoMark : logoMarkLight) : isDark ? logoFull : logoFullLight}
            alt="BeatzClik"
            className={cn("w-auto", collapsed ? "h-9" : "h-10")}
          />
        </Link>
        <button
          onClick={() => setCollapsed((c) => !c)}
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          title={collapsed ? "Expand" : "Collapse"}
          className="w-8 h-8 rounded-full flex items-center justify-center text-gray-500 dark:text-gray-300 hover:text-black dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors shrink-0"
        >
          {collapsed ? <ChevronsRight size={18} /> : <ChevronsLeft size={18} />}
        </button>
      </div>

      {/* Nav */}
      <nav className="flex flex-col gap-2 mt-2">
        {NAV.map(({ to, icon: Icon, label }) => (
          <SidebarTooltip key={to} label={label} enabled={collapsed}>
            <Link
              to={to}
              className={cn(
                "flex items-center rounded-md text-gray-600 dark:text-gray-300 hover:text-black dark:hover:text-white transition-colors [&.active]:text-beatz-green [&.active]:font-bold",
                collapsed ? "justify-center h-11 w-11 mx-auto" : "gap-4 px-2 py-2",
              )}
            >
              <Icon size={20} />
              {!collapsed && <span className="text-sm font-bold">{label}</span>}
            </Link>
          </SidebarTooltip>
        ))}
      </nav>

      {/* Playlists */}
      <div className="flex flex-col gap-2 mt-2 flex-1 overflow-y-auto no-scrollbar">
        <div className={cn("flex items-center", collapsed ? "flex-col gap-1" : "justify-between px-2 mb-1")}>
          {!collapsed && (
            <span className="text-[10px] font-mono text-gray-400 dark:text-gray-500 tracking-widest uppercase">Your Playlists</span>
          )}
          <SidebarTooltip label="Create playlist" enabled={collapsed}>
            <button
              onClick={() => setCreateOpen(true)}
              aria-label="Create playlist"
              className={cn(
                "rounded-md text-gray-500 hover:text-black dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 flex items-center justify-center transition-colors",
                collapsed ? "w-11 h-11 mx-auto" : "w-7 h-7",
              )}
            >
              <Plus size={18} />
            </button>
          </SidebarTooltip>
        </div>

        <PlaylistLink to="/library" name="Liked Songs" collapsed={collapsed} gradient="bg-gradient-to-br from-purple-400 to-purple-600" icon={<Heart size={13} className="text-white" fill="white" />} />
        {userPlaylists.map((p) => (
          <PlaylistLink
            key={p.id}
            to={`/playlist/${p.id}`}
            name={p.title}
            collapsed={collapsed}
            gradient="bg-gradient-to-br from-beatz-green/50 to-beatz-blue/50"
            icon={<ListMusic size={13} className="text-white" />}
          />
        ))}
      </div>

      {/* Footer */}
      <div className={cn("mt-auto pt-4 border-t border-gray-200 dark:border-white/5 flex flex-col gap-1", collapsed && "items-center")}>
        <SidebarTooltip label="Artist Studio" enabled={collapsed}>
          <Link
            to="/studio"
            className={cn(
              "flex items-center text-gray-600 dark:text-gray-300 hover:text-beatz-green transition-colors text-sm font-bold",
              collapsed ? "justify-center w-11 h-11 rounded-md hover:bg-gray-100 dark:hover:bg-white/10" : "gap-3 px-2 py-2",
            )}
          >
            <Disc3 size={18} />
            {!collapsed && <span>Artist Studio</span>}
          </Link>
        </SidebarTooltip>
        <SidebarTooltip label="Install desktop app" enabled={collapsed}>
          <button
            className={cn(
              "flex items-center text-gray-600 dark:text-gray-300 hover:text-black dark:hover:text-white transition-colors text-sm",
              collapsed ? "justify-center w-11 h-11 rounded-md hover:bg-gray-100 dark:hover:bg-white/10" : "gap-3 px-2 py-2",
            )}
          >
            <Download size={18} />
            {!collapsed && <span>Install desktop app</span>}
          </button>
        </SidebarTooltip>
      </div>

      <CreatePlaylistModal
        isOpen={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(id) => navigate({ to: '/playlist/$playlistId', params: { playlistId: id } })}
      />
    </aside>
  )
}

function PlaylistLink({
  to,
  name,
  gradient,
  icon,
  collapsed,
}: {
  to: string
  name: string
  gradient: string
  icon?: ReactNode
  collapsed?: boolean
}) {
  return (
    <SidebarTooltip label={name} enabled={!!collapsed}>
      <Link to={to} className="block w-full">
        <div
          className={cn(
            "flex items-center rounded-md cursor-pointer transition-colors",
            collapsed ? "justify-center p-1.5 mx-auto" : "gap-3 px-2 py-1.5 hover:bg-gray-50 dark:hover:bg-white/5",
          )}
        >
          <div className={cn("w-7 h-7 rounded flex items-center justify-center shrink-0", gradient)}>
            {icon ?? <div className="w-2.5 h-2.5 rounded-full border border-white/50 opacity-50" />}
          </div>
          {!collapsed && (
            <span className="text-sm truncate text-gray-600 dark:text-gray-300 hover:text-black dark:hover:text-white">
              {name}
            </span>
          )}
        </div>
      </Link>
    </SidebarTooltip>
  )
}
