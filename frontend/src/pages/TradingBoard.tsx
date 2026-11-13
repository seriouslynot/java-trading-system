import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import type { Book, Depth, Side } from '../api'
import { cents, errText, integer, moneyInput } from '../format'
import { Badge } from '../components/Badge'

function DepthTable({rows, side}: {rows: Depth[]; side: Side}) {
  const max = Math.max(1, ...rows.map(r => r.totalQuantity))
  return <div className={side === 'BUY' ? 'buy' : 'sell'}>
    <h3>{side === 'BUY' ? '买盘' : '卖盘'}</h3>
    <table><thead><tr><th>价格</th><th>数量</th><th>订单</th></tr></thead>
      <tbody>{rows.map(r => <tr key={r.price} style={{background: `linear-gradient(270deg, var(--depth) ${100 * r.totalQuantity / max}%, transparent 0)`}}>
        <td>{cents(r.price)}</td><td>{integer(r.totalQuantity)}</td><td>{r.orderCount}</td>
      </tr>)}</tbody></table>{!rows.length && <p className="empty">暂无挂单</p>}
  </div>
}

export function TradingBoard({book, symbol}: {book?: Book; symbol: string}) {
  const [client, setClient] = useState('A')
  const [side, setSide] = useState<Side>('BUY')
  const [price, setPrice] = useState('100.00')
  const [qty, setQty] = useState(5)
  const [key, setKey] = useState('')
  const [orderId, setOrderId] = useState('')
  const [cancelClient, setCancelClient] = useState('A')
  const [result, setResult] = useState({message: '', bad: false})
  const [cancelResult, setCancelResult] = useState({message: '', bad: false})
  const [placing, setPlacing] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  async function place(e: FormEvent) {
    e.preventDefault()
    setPlacing(true)
    try {
      const r = await api.place({clientId: client, side, price: moneyInput(price), quantity: qty, clientOrderId: key || undefined})
      setResult({bad: r.outcome !== 'SUCCESS', message: r.order
        ? `订单 #${r.order.orderId} · ${r.order.status} · 成交 ${r.order.filledQuantity} 股${r.duplicate ? ' · 重复请求' : ''}${r.reason ? ' · ' + r.reason : ''}`
        : r.reason ?? r.outcome})
      if (r.order && r.order.remainingQuantity > 0) { setOrderId(String(r.order.orderId)); setCancelClient(client) }
    } catch (error) { setResult({message: errText(error), bad: true}) }
    finally { setPlacing(false) }
  }
  async function cancel(e: FormEvent) {
    e.preventDefault()
    setCancelling(true)
    try {
      const r = await api.cancel(Number(orderId), cancelClient)
      setCancelResult({message: r.reason ?? `订单 #${orderId} 已撤销`, bad: r.outcome !== 'SUCCESS'})
    } catch (error) { setCancelResult({message: errText(error), bad: true}) }
    finally { setCancelling(false) }
  }
  return <>
    <div className="page-title"><div><p className="eyebrow">市场 / {symbol}</p><h1>交易看板</h1></div><Badge tone="muted">序列号 {integer(book?.journalSeq)}</Badge></div>
    <div className="stats">
      <div className="panel stat"><span>最优买价</span><strong className="buy">{cents(book?.bestBid)}</strong></div>
      <div className="panel stat"><span>最优卖价</span><strong className="sell">{cents(book?.bestAsk)}</strong></div>
      <div className="panel stat"><span>买卖价差</span><strong>{book?.bestBid != null && book.bestAsk != null ? cents(book.bestAsk - book.bestBid) : '—'}</strong></div>
      <div className="panel stat"><span>最新成交</span><strong>{cents(book?.recentTrades[0]?.price)}</strong></div>
    </div>
    <div className="layout">
      <section className="panel"><h2>订单簿 <small>每侧前 10 档</small></h2>
        <div className="depth"><DepthTable rows={book?.bids ?? []} side="BUY"/><DepthTable rows={book?.asks ?? []} side="SELL"/></div>
      </section>
      <section className="panel"><h2>限价下单</h2>
        <form onSubmit={place} className="form">
          <div className="toggle"><button type="button" className={side === 'BUY' ? 'active buy' : ''} onClick={() => setSide('BUY')}>买入</button><button type="button" className={side === 'SELL' ? 'active sell' : ''} onClick={() => setSide('SELL')}>卖出</button></div>
          <label>客户 ID<input required value={client} onChange={e => setClient(e.target.value)}/></label>
          <div className="row"><label>价格<input required inputMode="decimal" value={price} onChange={e => setPrice(e.target.value)}/></label>
            <label>数量（股）<input type="number" required min="1" max="1000000" value={qty} onChange={e => setQty(e.target.valueAsNumber)}/></label></div>
          <label>客户端订单号（可选）<input value={key} onChange={e => setKey(e.target.value)} placeholder="用于重复提交去重"/></label>
          <button className="primary" disabled={placing}>提交{side === 'BUY' ? '买单' : '卖单'}</button>
          {result.message && <p role="status" className={result.bad ? 'message error' : 'message'}>{result.message}</p>}
        </form>
      </section>
      <section className="panel"><h2>最近成交 <small>最多 50 笔</small></h2>
        <div className="scroll"><table><thead><tr><th>成交号</th><th>价格</th><th>数量</th><th>买方</th><th>卖方</th><th>主动方</th></tr></thead>
          <tbody>{book?.recentTrades.map(t => <tr key={t.tradeId}><td>#{t.tradeId}</td><td>{cents(t.price)}</td><td>{integer(t.quantity)}</td><td>{t.buyClientId}</td><td>{t.sellClientId}</td><td><Badge tone={t.takerSide === 'BUY' ? 'good' : 'bad'}>{t.takerSide === 'BUY' ? '买入' : '卖出'}</Badge></td></tr>)}</tbody></table></div>
        {!book?.recentTrades.length && <p className="empty">暂无成交</p>}
      </section>
      <section className="panel"><h2>撤单</h2><form onSubmit={cancel} className="form">
        <label>订单号<input type="number" min="1" required value={orderId} onChange={e => setOrderId(e.target.value)}/></label>
        <label>客户 ID<input required value={cancelClient} onChange={e => setCancelClient(e.target.value)}/></label>
        <button disabled={cancelling}>撤销挂单</button>
        {cancelResult.message && <p role="status" className={cancelResult.bad ? 'message error' : 'message'}>{cancelResult.message}</p>}
      </form></section>
    </div>
  </>
}
