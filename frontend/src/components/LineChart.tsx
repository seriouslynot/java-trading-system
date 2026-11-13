const W = 660, H = 182, X = 48, R = 644, T = 14, B = 152
type Series = {label: string; color: string; values: number[]}
export function LineChart({title, series, unit = '', labels}: {title: string; series: Series[]; unit?: string; labels: string[]}) {
  const max = Math.max(1, ...series.flatMap(s => s.values))
  const x = (i: number) => X + i * (R - X) / Math.max(1, labels.length - 1)
  const y = (value: number) => B - value * (B - T) / max
  return <figure className="panel chart"><figcaption>{title}</figcaption>
    <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={title}>
      {[0, .25, .5, .75, 1].map(p => <g key={p}>
        <line x1={X} x2={R} y1={y(max * p)} y2={y(max * p)} stroke="var(--edge)"/>
        <text x={X - 6} y={y(max * p) + 4} textAnchor="end">{Number((max * p).toPrecision(3))}{unit}</text>
      </g>)}
      {series.map(s => <polyline key={s.label} fill="none" stroke={s.color} strokeWidth="2" strokeLinejoin="round"
        points={s.values.map((v, i) => `${x(i)},${y(v)}`).join(' ')}/>)}
      {labels.length > 0 && [0, Math.floor(labels.length / 2), labels.length - 1].filter((v, i, a) => a.indexOf(v) === i)
        .map((i, idx) => <text key={i} x={x(i)} y={172} textAnchor={idx === 0 ? 'start' : i === labels.length - 1 ? 'end' : 'middle'}>{labels[i]}</text>)}
    </svg>
    <div className="legend">{series.map(s => <span key={s.label}><i style={{background: s.color}}/>{s.label}</span>)}</div>
  </figure>
}
