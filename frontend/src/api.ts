export type Side = 'BUY' | 'SELL'
export type Trade = { tradeId: number; sequence: number; price: number; quantity: number; buyClientId: string; sellClientId: string; takerSide: Side }
export type Order = { orderId: number; clientId: string; price: number; quantity: number; side: Side; filledQuantity: number; remainingQuantity: number; cancelledQuantity: number; status: string }
export type Depth = { price: number; totalQuantity: number; orderCount: number }
export type Account = { accountId: string; cashAvailable: number; cashFrozen: number; positionAvailable: number; positionFrozen: number }
export type Posting = { reference: string; sequence: number; accountId: string; asset: string; amount: number }
export type Book = { journalSeq: number; bestBid: number | null; bestAsk: number | null; bids: Depth[]; asks: Depth[]; recentTrades: Trade[]; accounts: Account[]; recentPostings: Posting[] }
export type Outcome = 'SUCCESS' | 'BUSINESS_REJECTED' | 'RATE_LIMITED' | 'QUEUE_FULL' | 'TIMEOUT'
export type OrderResult = { outcome: Outcome; order: Order | null; trades: Trade[]; reason: string | null; duplicate: boolean }
export type CancelResult = { outcome: Outcome; status: string | null; reason: string | null }
export type Anomaly = { type: string; reference: string | null; journalSeq: number; accountId: string; asset: string; expected: number | null; actual: number | null; description: string }
export type Recon = { report: { uptoSeq: number; expectedPostings: number; actualPostings: number; anomalies: Anomaly[] }; score: { injected: number; detected: number; missed: number; falsePositives: number; balanceAnomalies: number; injectedByType: Record<string, number>; detectedByType: Record<string, number> } }
export type Experiment = Recon & { commands: number; trades: number; rate: number; seed: number }
export type Protection = { rateLimitEnabled: boolean; queueLimitEnabled: boolean; cancelPriorityEnabled: boolean; queueCapacity: number; rateLimitBurst: number; rateLimitPerSecond: number }
export type Surge = { running: boolean; ratePerSecond: number; seconds: number; submitted: number; completed: number; error: string | null }
export type Ops = { protection: Protection; queueDepth: number; surge: Surge; coreFailed: boolean }
export type Second = { epochSecond: number; requests: number; success: number; businessRejected: number; rateLimited: number; queueFull: number; timeouts: number; p99Millis: number; queueDepth: number; queueCapacity: number; reconAnomalies: number }
export type Alerts = { states: Record<string, string>; events: { rule: string; state: string; epochSecond: number; description: string }[]; rules: { name: string; description: string; forSeconds: number }[] }

export const token = {
  get: () => localStorage.getItem('trading-admin-token') ?? '',
  set: (value: string) => { if (value) localStorage.setItem('trading-admin-token', value); else localStorage.removeItem('trading-admin-token') },
}

async function request<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal, outcome = false): Promise<T> {
  const headers = new Headers()
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  if (method === 'POST' && /^\/api\/(ops|recon)\//.test(path) && token.get()) headers.set('X-Admin-Token', token.get())
  const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal })
  if (response.status === 204) return null as T
  const data = await response.json().catch(() => null)
  if (!response.ok && !(outcome && data?.outcome)) throw new Error(data?.reason ?? `请求失败（HTTP ${response.status}）`)
  return data as T
}

export const api = {
  book: (signal?: AbortSignal) => request<Book>('/api/book', 'GET', undefined, signal),
  info: (signal?: AbortSignal) => request<{symbol: string}>('/api/info', 'GET', undefined, signal),
  order: (id: number) => request<Order>(`/api/orders/${id}`),
  place: (body: { clientId: string; side: Side; price: number; quantity: number; clientOrderId?: string }) =>
    request<OrderResult>('/api/orders', 'POST', body, undefined, true),
  cancel: (id: number, client: string) => request<CancelResult>(`/api/orders/${id}?clientId=${encodeURIComponent(client)}`, 'DELETE', undefined, undefined, true),
  latest: (signal?: AbortSignal) => request<Recon | null>('/api/recon/latest', 'GET', undefined, signal),
  injection: (signal?: AbortSignal) => request<{rate: number}>('/api/recon/injection', 'GET', undefined, signal),
  setInjection: (rate: number) => request<{rate: number}>('/api/recon/injection', 'POST', {rate}),
  recon: () => request<Recon>('/api/recon/run', 'POST'),
  experiment: (orders: number, rate: number, seed: number) => request<Experiment>('/api/recon/experiment', 'POST', {orders, rate, seed}),
  ops: (signal?: AbortSignal) => request<Ops>('/api/ops/status', 'GET', undefined, signal),
  metrics: (signal?: AbortSignal) => request<Second[]>('/api/ops/metrics?seconds=60', 'GET', undefined, signal),
  alerts: (signal?: AbortSignal) => request<Alerts>('/api/ops/alerts', 'GET', undefined, signal),
  protection: (body: Partial<Protection>) => request<Protection>('/api/ops/protection', 'POST', body),
  start: (ratePerSecond: number, seconds: number) => request<Surge>('/api/ops/surge', 'POST', {ratePerSecond, seconds}),
  stop: () => request<Surge>('/api/ops/surge/stop', 'POST'),
}
