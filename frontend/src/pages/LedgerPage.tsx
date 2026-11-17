import { useState } from 'react'
import { api } from '../api'
import type { Book, Recon, Experiment } from '../api'
import { usePolling } from '../usePolling'
import { cents, errText, integer } from '../format'
import { Badge } from '../components/Badge'

function Result({value}: {value: Recon | null | undefined}) {
  if (!value) return <p className="empty">尚未运行对账</p>
  const {report, score} = value
  return <>
    <div className="summary"><Badge tone={report.anomalies.length ? 'bad' : 'good'}>{report.anomalies.length ? `${report.anomalies.length} 条差异记录` : '对账通过'}</Badge>
      <span>截至 #{integer(report.uptoSeq)}</span><span>预期 / 实际：{integer(report.expectedPostings)} / {integer(report.actualPostings)}</span></div>
    <p className="muted">注入事件 {score.injected} · 识别 {score.detected} · 漏报 {score.missed} · 误报 {score.falsePositives} · 余额差异 {score.balanceAnomalies}</p>
    {report.anomalies.length > 0 && <div className="scroll"><table><thead><tr><th>差异</th><th>来源</th><th>序列号</th><th>账户</th><th>资产</th><th>预期</th><th>实际</th></tr></thead>
      <tbody>{report.anomalies.slice(0, 100).map((a, i) => <tr key={i}><td>{a.description}</td><td>{a.reference ?? '—'}</td><td>{a.journalSeq}</td><td>{a.accountId}</td><td>{a.asset}</td><td>{integer(a.expected)}</td><td>{integer(a.actual)}</td></tr>)}</tbody></table></div>}
    {report.anomalies.length > 100 && <p className="muted">仅显示前 100 条差异</p>}
  </>
}

export function LedgerPage({book}: {book?: Book}) {
  const latest = usePolling(api.latest)
  const current = usePolling(api.injection)
  const [rate, setRate] = useState(1)
  const [orders, setOrders] = useState(3000)
  const [experimentRate, setExperimentRate] = useState(1)
  const [seed, setSeed] = useState(1)
  const [result, setResult] = useState<Experiment>()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState({text: '', error: false})
  async function run(task: () => Promise<string>) {
    setBusy(true)
    try { setMessage({text: await task(), error: false}) }
    catch (e) { setMessage({text: errText(e), error: true}) }
    finally { setBusy(false) }
  }
  return <>
    <div className="page-title"><div><p className="eyebrow">结算 / 审计</p><h1>账本与对账</h1></div><Badge>序列号 {integer(book?.journalSeq)}</Badge></div>
    <div className="layout">
      <section className="panel"><h2>账户余额 <small>现金 / 持仓</small></h2><div className="scroll"><table>
        <thead><tr><th>账户</th><th>可用现金</th><th>冻结现金</th><th>可用持仓</th><th>冻结持仓</th></tr></thead>
        <tbody>{book?.accounts.map(a => <tr key={a.accountId}><td>{a.accountId}</td><td>{cents(a.cashAvailable)}</td><td>{cents(a.cashFrozen)}</td><td>{integer(a.positionAvailable)}</td><td>{integer(a.positionFrozen)}</td></tr>)}</tbody>
      </table></div></section>
      <section className="panel"><h2>在线对账</h2>
        <p className="muted">按日志序列号比对分录与交易账户余额。</p>
        <button className="primary" disabled={busy} onClick={() => void run(async () => { await api.recon(); return '对账已完成，结果将自动刷新' })}>运行对账</button>
        <form className="form gap" onSubmit={e => { e.preventDefault(); void run(async () => `注入比例已设为 ${(await api.setInjection(rate / 100)).rate * 100}%`) }}>
          <label>异常注入比例（%）<input type="number" min="0" max="100" step="any" required value={rate} onChange={e => setRate(e.target.valueAsNumber)}/></label>
          <button disabled={busy}>设置注入比例</button>
          <small>当前比例：{current.value == null ? '—' : `${current.value.rate * 100}%`}</small>
        </form>
        {message.text && <p role="status" className={message.error ? 'message error' : 'message'}>{message.text}</p>}
        {latest.error && <p className="message error">{latest.error}</p>}
      </section>
      <section className="panel wide"><h2>最近一次对账结果</h2><Result value={latest.value}/></section>
      <section className="panel"><h2>最近分录 <small>显示最近 100 条</small></h2><div className="scroll"><table>
        <thead><tr><th>来源</th><th>序列号</th><th>账户</th><th>资产</th><th>变动</th></tr></thead>
        <tbody>{book?.recentPostings.slice(0, 100).map((p, i) => <tr key={i}><td>{p.reference}</td><td>{p.sequence}</td><td>{p.accountId}</td><td>{p.asset === 'CASH' ? '现金' : '持仓'}</td><td className={p.amount < 0 ? 'sell' : 'buy'}>{p.amount > 0 ? '+' : ''}{p.asset === 'CASH' ? cents(p.amount) : integer(p.amount)}</td></tr>)}</tbody>
      </table></div></section>
      <section className="panel"><h2>独立对账实验</h2>
        <form className="form" onSubmit={e => { e.preventDefault(); void run(async () => {
          const r = await api.experiment(orders, experimentRate / 100, seed); setResult(r); return `实验完成：${r.commands} 条命令，${r.trades} 笔成交`
        }) }}>
          <label>命令数（约 80% 下单、20% 撤单）<input type="number" min="1" max="100000" required value={orders} onChange={e => setOrders(e.target.valueAsNumber)}/></label>
          <div className="row"><label>注入比例（%）<input type="number" min="0" max="100" step="any" required value={experimentRate} onChange={e => setExperimentRate(e.target.valueAsNumber)}/></label>
            <label>随机种子<input type="number" required value={seed} onChange={e => setSeed(e.target.valueAsNumber)}/></label></div>
          <button disabled={busy}>运行实验</button>
        </form>
        {result && <Result value={result}/>}
      </section>
    </div>
  </>
}
