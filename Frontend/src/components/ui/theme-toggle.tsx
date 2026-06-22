import { Moon, Sun } from "lucide-react"
import { useTheme } from "../theme-provider"
import { Button } from "./button"

export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme()

  return (
    <Button
      variant="ghost"
      size="sm"
      className="w-10 h-10 p-0 rounded-full"
      onClick={() => setTheme(resolvedTheme === "light" ? "dark" : "light")}
      aria-label="Toggle theme"
    >
      {resolvedTheme === "light" ? (
        <Moon className="h-[1.2rem] w-[1.2rem] transition-all" />
      ) : (
        <Sun className="h-[1.2rem] w-[1.2rem] transition-all" />
      )}
    </Button>
  )
}
