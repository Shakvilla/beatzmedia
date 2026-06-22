import { cn } from "../../../utils/cn"

interface BrowseCardProps {
  title: string
  colorClass: string
  className?: string
}

export function BrowseCard({ title, colorClass, className }: BrowseCardProps) {
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-xl p-4 aspect-[2/1] cursor-pointer hover:scale-[1.02] transition-transform duration-300 group shadow-md",
        colorClass,
        className
      )}
    >
      <h3 className="text-white font-bold text-xl md:text-2xl z-10 relative">
        {title}
      </h3>
      
      {/* 
        This div mimics the decorative element in the bottom right corner of the Spotify/Beatzclik browse cards.
        It has a slight rotation and is partially cut off by the parent's overflow-hidden.
      */}
      <div className="absolute -right-4 -bottom-2 w-20 h-20 bg-black/20 rounded-md rotate-[25deg] shadow-lg flex items-center justify-center backdrop-blur-sm group-hover:scale-110 transition-transform duration-300">
         {/* Using the half-moon icon from the screenshot as a placeholder graphic */}
         <div className="w-8 h-8 rounded-full border-2 border-white/30 flex overflow-hidden">
            <div className="w-1/2 h-full bg-white/30" />
         </div>
      </div>
    </div>
  )
}
