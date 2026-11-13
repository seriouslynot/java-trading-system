const decimal = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const cents = (value: number | null | undefined) => value == null ? '—' : decimal.format(value / 100)
export const integer = (value: number | null | undefined) => value == null ? '—' : value.toLocaleString('zh-CN')
export const moneyInput = (text: string) => {
  if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('价格请输入最多两位小数')
  const [whole, decimal = ''] = text.split('.')
  const value = Number(whole) * 100 + Number(decimal.padEnd(2, '0'))
  if (!Number.isSafeInteger(value) || value > 100_000_000) throw new Error('价格超过上限')
  return value
}
export const errText = (error: unknown) => error instanceof Error ? error.message : '请求失败'
