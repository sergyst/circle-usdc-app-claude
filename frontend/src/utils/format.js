export function formatUsdc(amount) {
  const n = Number(amount)
  if (Number.isNaN(n)) return amount
  return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 })
}

export function formatDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export function shortAddress(addr) {
  if (!addr || addr.length < 12) return addr || '—'
  return `${addr.slice(0, 6)}…${addr.slice(-4)}`
}
