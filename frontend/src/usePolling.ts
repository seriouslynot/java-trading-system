import { useEffect, useState } from 'react'

export function usePolling<T>(load: (signal: AbortSignal) => Promise<T>, interval = 1000) {
  const [value, setValue] = useState<T>()
  const [error, setError] = useState('')
  useEffect(() => {
    const ctrl = new AbortController()
    let timer: ReturnType<typeof setTimeout>
    async function poll() {
      try {
        const data = await load(ctrl.signal)
        if (!ctrl.signal.aborted) { setValue(data); setError('') }
      } catch (e) {
        if (!ctrl.signal.aborted) setError(e instanceof Error ? e.message : '连接失败')
      }
      if (!ctrl.signal.aborted) timer = setTimeout(poll, interval)
    }
    void poll()
    return () => { ctrl.abort(); clearTimeout(timer) }
  }, [load, interval])
  return { value, error }
}
