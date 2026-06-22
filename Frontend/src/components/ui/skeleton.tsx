import { cn } from "../../utils/cn"

function Skeleton({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("animate-pulse rounded-md bg-beatz-dark-surface-2 dark:bg-beatz-dark-surface-3 bg-opacity-20 dark:bg-opacity-100", className)}
      {...props}
    />
  )
}

export { Skeleton }
