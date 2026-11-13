import { useState } from 'react'
import { api, token } from './api'
import { usePolling } from './usePolling'
import { Badge } from './components/Badge'
import { TradingBoard } from './pages/TradingBoard'
import { LedgerPage } from './pages/LedgerPage'
import { OpsPage } from './pages/OpsPage'

const pages = ['交易看板', '账本与对账', '运营'] as const

export default function App() {
  const [page, setPage] = useState<typeof pages[number]>('交易看板')
  const [admin, setAdmin] = useState(() => token.get())
  const [edit, setEdit] = useState(false)
  const book = usePolling(api.book)
  const info = usePolling(api.info, 15000)
  const symbol = info.value?.symbol ?? 'DEMO'
  return <>
    <header><div className="nav shell">
      <div className="brand"><div aria-hidden="true" className="mark"><i/><i/><i/></div><div><strong>交易实验台</strong><small>MATCHING LAB</small></div></div>
      <nav aria-label="主导航">{pages.map(p => <button key={p} className={p === page ? 'selected' : ''} aria-current={p === page ? 'page' : undefined} onClick={() => setPage(p)}>{p}</button>)}</nav>
      <div className="nav-actions"><Badge tone={book.error ? 'bad' : book.value ? 'good' : 'muted'}>{book.error ? '连接异常' : book.value ? '已连接' : '正在连接'}</Badge>
        <button className="plain" onClick={() => setEdit(!edit)}>管理口令</button></div>
    </div></header>
    <main className="shell">
      {edit && <form className="panel summary token" onSubmit={e => { e.preventDefault(); token.set(admin); setEdit(false) }}>
        <label>管理口令<input autoFocus type="password" value={admin} onChange={e => setAdmin(e.target.value)} placeholder="留空则清除" autoComplete="off"/></label>
        <button>保存到当前浏览器</button>
      </form>}
      {book.error && <p className="message error" role="alert">{book.error}</p>}
      {page === '交易看板' && <TradingBoard symbol={symbol} book={book.value}/>}
      {page === '账本与对账' && <LedgerPage book={book.value}/>}
      {page === '运营' && <OpsPage/>}
      <footer>{symbol} 单交易对 · 行情每秒更新</footer>
    </main>
  </>
}
