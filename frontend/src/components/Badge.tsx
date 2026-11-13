import type { ReactNode } from 'react'
export function Badge({children, tone = 'muted'}: {children: ReactNode; tone?: 'good' | 'bad' | 'muted' | 'warn'}) {
  return <span className={`badge ${tone}`}><i aria-hidden="true"/>{children}</span>
}
