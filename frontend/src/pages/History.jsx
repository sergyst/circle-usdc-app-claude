import React, { useEffect, useState } from 'react'
import { listTransactions } from '../api/transfers'
import StatusPill from '../components/StatusPill.jsx'
import { formatUsdc, formatDate, shortAddress } from '../utils/format.js'

export default function History() {
  const [transactions, setTransactions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    listTransactions()
      .then(setTransactions)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div>
      <div className="page-header">
        <h1>Ledger history</h1>
        <p>Every on-chain transfer and faucet drip recorded against your wallets.</p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="card">
        {loading && <div className="empty-state">Loading…</div>}
        {!loading && transactions.length === 0 && (
          <div className="empty-state">
            <div className="title">Nothing recorded yet</div>
            <p className="muted">Transfers and faucet requests will appear here.</p>
          </div>
        )}
        {transactions.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Direction</th>
                <th className="num">Amount (USDC)</th>
                <th>Counterparty</th>
                <th>Status</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((tx) => (
                <tr key={tx.id}>
                  <td>{tx.type}</td>
                  <td className="faint">{tx.direction}</td>
                  <td className="num">{formatUsdc(tx.amountUsdc)}</td>
                  <td className="mono">{shortAddress(tx.counterparty)}</td>
                  <td>
                    <StatusPill state={tx.state} />
                  </td>
                  <td className="faint">{formatDate(tx.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
