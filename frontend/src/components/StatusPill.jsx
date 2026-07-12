import React from 'react'

const CONFIG = {
  COMPLETE: { cls: 'pill-complete', label: 'Complete' },
  PROCESSING: { cls: 'pill-pending', label: 'Processing' },
  PENDING: { cls: 'pill-pending', label: 'Pending' },
  AWAITING_FUNDS: { cls: 'pill-pending', label: 'Awaiting funds' },
  FAILED: { cls: 'pill-failed', label: 'Failed' }
}

export default function StatusPill({ state, simulated }) {
  const config = CONFIG[state] || { cls: 'pill-pending', label: state }
  return (
    <span className={`pill ${simulated ? 'pill-simulated' : config.cls}`}>
      {simulated ? 'Simulated' : config.label}
    </span>
  )
}
