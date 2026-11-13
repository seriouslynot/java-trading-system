import http from 'k6/http'
import { check } from 'k6'

const base = __ENV.BASE_URL || 'http://localhost:8080'
export const options = {
  scenarios: { orders: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 300), timeUnit: '1s',
    duration: __ENV.DURATION || '20s', preAllocatedVUs: 64, maxVUs: 512 } },
}

export default function () {
  const clientId = 'S' + String(1 + Math.floor(Math.random() * 20)).padStart(2, '0')
  const body = JSON.stringify({clientId, side: Math.random() < .5 ? 'BUY' : 'SELL',
    price: 9950 + Math.floor(Math.random() * 101), quantity: 1 + Math.floor(Math.random() * 100)})
  const r = http.post(base + '/api/orders', body, {headers: {'Content-Type': 'application/json'}})
  check(r, {'HTTP response expected': res => [200, 429, 503].includes(res.status)})
}
