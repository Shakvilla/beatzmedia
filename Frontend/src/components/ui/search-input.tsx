import * as React from "react"
import { Search } from "lucide-react"
import { cn } from "../../utils/cn"

export function SearchInput({ className, ...props }: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className={cn("relative flex items-center", className)}>
      <Search className="absolute left-4 text-beatz-dark-surface-3 dark:text-beatz-light-surface-3" size={20} />
      <input
        className="w-full h-12 pl-12 pr-4 rounded-full bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white placeholder-beatz-dark-surface-3 dark:placeholder-beatz-light-surface-3 focus:outline-none focus:ring-2 focus:ring-beatz-green transition-shadow"
        placeholder="Search..."
        {...props}
      />
    </div>
  )
}
