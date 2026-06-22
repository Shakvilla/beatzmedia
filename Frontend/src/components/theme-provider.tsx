import * as React from "react"

export type Theme = "dark" | "light" | "system"
/** The actual applied theme after resolving "system". */
export type ResolvedTheme = "dark" | "light"

type ThemeProviderProps = {
  children: React.ReactNode
  defaultTheme?: Theme
  storageKey?: string
}

type ThemeProviderState = {
  /** The user's choice, which may be "system". */
  theme: Theme
  /** The concrete theme currently applied ("dark" | "light"). */
  resolvedTheme: ResolvedTheme
  setTheme: (theme: Theme) => void
}

const initialState: ThemeProviderState = {
  theme: "dark",
  resolvedTheme: "dark",
  setTheme: () => null,
}

const ThemeProviderContext = React.createContext<ThemeProviderState>(initialState)

const systemTheme = (): ResolvedTheme =>
  typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark"

export function ThemeProvider({
  children,
  defaultTheme = "dark",
  storageKey = "beatzclik-theme",
  ...props
}: ThemeProviderProps) {
  const [theme, setThemeState] = React.useState<Theme>(
    () => (typeof window !== "undefined" && (localStorage.getItem(storageKey) as Theme)) || defaultTheme
  )
  const [resolvedTheme, setResolvedTheme] = React.useState<ResolvedTheme>(
    () => (theme === "system" ? systemTheme() : theme)
  )

  React.useEffect(() => {
    const apply = () => {
      const resolved = theme === "system" ? systemTheme() : theme
      setResolvedTheme(resolved)
      const root = window.document.documentElement
      root.classList.remove("light", "dark")
      root.classList.add(resolved)
    }
    apply()

    // Re-resolve when the OS preference changes, but only while on "system".
    if (theme !== "system") return
    const mql = window.matchMedia("(prefers-color-scheme: light)")
    mql.addEventListener("change", apply)
    return () => mql.removeEventListener("change", apply)
  }, [theme])

  const value = React.useMemo(
    () => ({
      theme,
      resolvedTheme,
      setTheme: (next: Theme) => {
        localStorage.setItem(storageKey, next)
        setThemeState(next)
      },
    }),
    [theme, resolvedTheme, storageKey]
  )

  return (
    <ThemeProviderContext.Provider {...props} value={value}>
      {children}
    </ThemeProviderContext.Provider>
  )
}

export const useTheme = () => {
  const context = React.useContext(ThemeProviderContext)
  if (context === undefined)
    throw new Error("useTheme must be used within a ThemeProvider")
  return context
}
