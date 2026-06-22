import * as React from "react"
import { Play } from "lucide-react"
import { cn } from "../../utils/cn"
import { Skeleton } from "./skeleton"
import { Link } from "@tanstack/react-router"

const CardContext = React.createContext<{ hover: boolean }>({ hover: false })

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  to?: string
}

export function Card({ className, children, to, ...props }: CardProps) {
  const [hover, setHover] = React.useState(false)

  const content = (
    <div
      className={cn("group flex flex-col gap-3 cursor-pointer", className)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      {...props}
    >
      {children}
    </div>
  )

  return (
    <CardContext.Provider value={{ hover }}>
      {to ? <Link to={to}>{content}</Link> : content}
    </CardContext.Provider>
  )
}

interface CardImageProps extends React.ImgHTMLAttributes<HTMLImageElement> {
  aspectRatio?: "square" | "video" | "auto"
  fallbackIcon?: React.ReactNode
  /** When set, the hover overlay becomes a play button (so the card itself can navigate). */
  onPlay?: () => void
}

export function CardImage({ className, src, alt, aspectRatio = "square", fallbackIcon, onPlay, ...props }: CardImageProps) {
  const [isLoading, setIsLoading] = React.useState(true)
  const { hover } = React.useContext(CardContext)

  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-md bg-beatz-dark-surface-2 dark:bg-beatz-dark-surface-3 transition-all duration-300 shadow-md",
        aspectRatio === "square" && "aspect-square",
        aspectRatio === "video" && "aspect-video",
        hover && "shadow-xl dark:shadow-beatz-green/5 -translate-y-1",
        className
      )}
    >
      {/* Loading Skeleton */}
      {isLoading && src && (
        <Skeleton className="absolute inset-0 z-0" />
      )}

      {/* Fallback Icon */}
      {!src && fallbackIcon && (
        <div className="absolute inset-0 flex items-center justify-center text-beatz-light-surface-3 dark:text-beatz-dark-surface-3 z-0">
          {fallbackIcon}
        </div>
      )}

      {/* Actual Image */}
      {src && (
        <img
          src={src}
          alt={alt || ""}
          className={cn(
            "object-cover w-full h-full transition-opacity duration-500 z-10 relative",
            isLoading ? "opacity-0" : "opacity-100"
          )}
          onLoad={() => setIsLoading(false)}
          {...props}
        />
      )}

      {/* Overlay Play Button */}
      {onPlay ? (
        <button
          type="button"
          aria-label="Play"
          onClick={(e) => {
            e.preventDefault()
            e.stopPropagation()
            onPlay()
          }}
          className={cn(
            "absolute right-3 bottom-3 z-20 flex items-center justify-center w-12 h-12 rounded-full bg-beatz-green shadow-lg transition-all duration-300 transform hover:scale-105",
            hover ? "translate-y-0 opacity-100" : "translate-y-4 opacity-0"
          )}
        >
          <Play fill="black" className="text-black ml-1" size={24} />
        </button>
      ) : (
        <div
          className={cn(
            "absolute right-3 bottom-3 z-20 flex items-center justify-center w-12 h-12 rounded-full bg-beatz-green shadow-lg transition-all duration-300 transform",
            hover ? "translate-y-0 opacity-100" : "translate-y-4 opacity-0"
          )}
        >
          <Play fill="black" className="text-black ml-1" size={24} />
        </div>
      )}
    </div>
  )
}

export function CardContent({ className, children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn("flex flex-col gap-1", className)} {...props}>
      {children}
    </div>
  )
}

export function CardTitle({ className, children, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={cn(
        "font-bold text-base text-beatz-dark-bg dark:text-white truncate group-hover:text-beatz-green transition-colors",
        className
      )}
      {...props}
    >
      {children}
    </h3>
  )
}

export function CardSubtitle({ className, children, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return (
    <p
      className={cn(
        "text-sm text-beatz-dark-surface-3 dark:text-beatz-light-surface-3 truncate",
        className
      )}
      {...props}
    >
      {children}
    </p>
  )
}

export function QuickPickCard({
  title,
  icon,
  to,
  className,
  ...props
}: { title: string; icon: React.ReactNode; to?: string } & React.HTMLAttributes<HTMLDivElement>) {
  const content = (
    <div
      className={cn(
        "group flex items-center gap-4 bg-white dark:bg-beatz-dark-surface-2 hover:bg-gray-100 dark:hover:bg-beatz-dark-surface-3 rounded-md overflow-hidden cursor-pointer transition-colors shadow-sm",
        className
      )}
      {...props}
    >
      <div className="w-20 h-20 flex items-center justify-center shrink-0">
         {icon}
      </div>
      <span className="font-bold text-base text-beatz-dark-bg dark:text-white flex-1 truncate pr-4">
        {title}
      </span>
      <div className="mr-4 w-10 h-10 rounded-full bg-beatz-green shadow-lg flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
         <Play fill="black" className="text-black ml-1" size={20} />
      </div>
    </div>
  )

  return to ? <Link to={to} className="block">{content}</Link> : content
}
