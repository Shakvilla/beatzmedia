import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/studio/release/new/')({
  beforeLoad: () => {
    throw redirect({ to: '/studio/release/new/details' })
  },
})
