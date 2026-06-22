import * as React from 'react'
import { Button as BaseButton } from '@base-ui/react/button'
import type { ButtonProps as BaseButtonProps } from '@base-ui/react/button'
import { cn } from '../../utils/cn'

export interface ButtonProps extends BaseButtonProps {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', ...props }, ref) => {
    const variants = {
      primary: 'bg-beatz-green text-black hover:scale-105 active:scale-95',
      secondary: 'bg-beatz-dark-surface-2 text-white hover:bg-beatz-dark-surface-3',
      outline: 'border-2 border-beatz-green text-beatz-green bg-transparent hover:bg-beatz-green/10',
      ghost: 'bg-transparent text-white hover:bg-white/10',
    }

    const sizes = {
      sm: 'px-4 py-2 text-sm',
      md: 'px-8 py-3 text-base',
      lg: 'px-10 py-4 text-lg',
    }

    return (
      <BaseButton
        {...props}
        ref={ref}
        className={cn(
          'font-bold rounded-full transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed',
          variants[variant],
          sizes[size],
          className
        )}
      />
    )
  }
)

Button.displayName = 'Button'

export { Button }
