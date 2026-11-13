import { useState } from 'react'
import { api } from '../api'
import type { Protection } from '../api'
import { usePolling } from '../usePolling'
import { errText, integer } from '../format'
import { Badge } from '../components/Badge'
import { LineChart } from '../components/LineChart'

const switches = [
  ['rateLimitEnabled', '令牌桶限流'], ['queueLimitEnabled', '有界队列'], ['cancelPriorityEnabled', '撤单优先'],
] as const

export function OpsPage() {
  const {value: status, error} = usePolling(api.ops)
  const {value: metrics} = usePolling(api.metrics)
  const {value: alerts} = usePolling(api.alerts)
  const [rate, setRate] = useState(5000)
  const [seconds, setSeconds] = useState(20)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState({text: '', error: false})
  const [params, setParams] = useState<Partial<Protection>>({})
  const surge = status?.surge
  async function change(task: () => Promise<unknown>, text: string) {
    setBusy(true)
    try { await task(); setMessage({text, error: false}) }
    catch (e) { setMessage({text: errText(e), error: true}) }
    finally { setBusy(false) }
  }
  const m = metrics ?? []
  const labels = m.map(s => new Date(s.epochSecond * 1000).toLocaleTimeString('zh-CN', {hour12: false}))
  const p = status?.protection
  const last = m.at(-1)
  return <>
    <div className="page-title"><div><p className="eyebrow">吞吐 / 防护</p><h1>运营控制台</h1></div><Badge tone={status?.coreFailed ? 'bad' : status ? 'good' : 'muted'}>{status?.coreFailed ? '核心停止' : status ? '核心运行中' : '正在连接'}</Badge></div>
    <div className="stats">
      <div className="panel stat"><span>上一秒请求</span><strong>{integer(last?.requests)}</strong></div>
      <div className="panel stat"><span>P99 延迟</span><strong>{last?.p99Millis.toFixed(1) ?? '—'} <small>ms</small></strong></div>
      <div className="panel stat"><span>队列深度</span><strong>{integer(status?.queueDepth)} <small>/ {integer(p?.queueCapacity)}</small></strong></div>
      <div className="panel stat"><span>对账差异</span><strong>{integer(last?.reconAnomalies)}</strong></div>
    </div>
    {(error || message.text) && <p role="status" className={error || message.error ? 'message error' : 'message'}>{error || message.text}</p>}
    <div className="layout even">
      <LineChart title="请求处理 / 最近 60 秒" labels={labels} series={[
        {label: '成功', color: '#42d4ae', values: m.map(s => s.success)}, {label: '业务拒绝', color: '#ffb767', values: m.map(s => s.businessRejected)},
        {label: '限流或队列满', color: '#91a9ff', values: m.map(s => s.rateLimited + s.queueFull)}, {label: '超时', color: '#ff7b8c', values: m.map(s => s.timeouts)},
      ]}/>
      <LineChart title="受理请求 P99 / 最近 60 秒" labels={labels} unit="" series={[{label: 'P99（ms）', color: '#91a9ff', values: m.map(s => s.p99Millis)}]}/>
      <section className="panel"><h2>防护设置</h2>
        <div className="summary">{switches.map(([key, label]) => <label className="switch" key={key}><input type="checkbox" checked={p?.[key] ?? false} disabled={!p || busy} onChange={e => void change(() => api.protection({[key]: e.target.checked}), `${label}设置已更新`)}/>{label}</label>)}</div>
        <div className="summary"><button disabled={busy} onClick={() => void change(() => api.protection({rateLimitEnabled: true, queueLimitEnabled: true, cancelPriorityEnabled: true}), '防护全部开启')}>全开</button>
          <button disabled={busy} onClick={() => void change(() => api.protection({rateLimitEnabled: false, queueLimitEnabled: false, cancelPriorityEnabled: false}), '防护全部关闭')}>全关</button></div>
        <form className="form" onSubmit={e => { e.preventDefault(); void change(async () => { await api.protection(params); setParams({}) }, '容量和速率已更新') }}>
          <div className="row three">{([['queueCapacity', '队列容量'], ['rateLimitBurst', '令牌桶容量'], ['rateLimitPerSecond', '令牌补充/秒']] as const).map(([key, label]) =>
            <label key={key}>{label}<input type="number" required min="1" value={params[key] ?? p?.[key] ?? ''} onChange={e => setParams({...params, [key]: e.target.valueAsNumber})}/></label>)}</div>
          <button disabled={!p || busy}>保存参数</button>
        </form>
      </section>
      <section className="panel"><h2>流量暴涨</h2><p className="muted">演示账户先撤销已有挂单，再按指定速率提交订单。</p>
        <form className="form" onSubmit={e => { e.preventDefault(); void change(() => api.start(rate, seconds), '流量暴涨已启动') }}>
          <div className="row"><label>请求/秒<input type="number" required min="1" max="20000" value={rate} onChange={e => setRate(e.target.valueAsNumber)}/></label>
            <label>持续秒数<input type="number" required min="1" max="120" value={seconds} onChange={e => setSeconds(e.target.valueAsNumber)}/></label></div>
          <div className="summary"><button className="primary" disabled={busy || surge?.running}>启动</button>
            <button type="button" disabled={busy || !surge?.running} onClick={() => void change(api.stop, '已请求停止')}>停止</button></div>
        </form>
        <div className="summary"><Badge tone={surge?.running ? 'warn' : 'muted'}>{surge?.running ? '运行中' : '空闲'}</Badge><span>已提交 {integer(surge?.submitted)} · 已处理 {integer(surge?.completed)}</span></div>
        {surge?.error && <p className="message error">{surge.error}</p>}
      </section>
      <section className="panel wide"><h2>告警</h2><div className="summary">
        {alerts?.rules.map(r => <span key={r.name} title={r.description}><Badge tone={alerts.states[r.name] === 'FIRING' ? 'bad' : 'good'}>{r.name}：{alerts.states[r.name] === 'FIRING' ? '触发' : '正常'}</Badge></span>)}
      </div><div className="scroll"><table><thead><tr><th>时间</th><th>规则</th><th>状态</th><th>条件</th></tr></thead><tbody>
        {alerts?.events.slice().reverse().map(e => <tr key={e.epochSecond + e.rule}><td>{new Date(e.epochSecond * 1000).toLocaleTimeString('zh-CN')}</td><td>{e.rule}</td><td>{e.state === 'FIRING' ? '触发' : '解除'}</td><td>{e.description}</td></tr>)}</tbody></table></div>
        {!alerts?.events.length && <p className="empty">暂无告警事件</p>}
      </section>
    </div>
  </>
}
