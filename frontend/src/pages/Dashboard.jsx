import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listWallets, getBalances } from '../api/wallets'
import { listTransactions } from '../api/transfers'
import { api } from '../api/client'
import StatusPill from '../components/StatusPill.jsx'
import { formatUsdc, formatDate, shortAddress } from '../utils/format.js'

export default function Dashboard() {
  const [wallets, setWallets] = useState([])
  const [totalUsdc, setTotalUsdc] = useState(null)
  const [transactions, setTransactions] = useState([])
  const [mintLive, setMintLive] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        setLoading(true)
        const [walletList, txList, health] = await Promise.all([
          listWallets(),
          listTransactions(),
          api.get('/api/health').then((r) => r.data)
        ])
        if (cancelled) return

        setWallets(walletList)
        setTransactions(txList.slice(0, 8))
        setMintLive(health.mintLive)

        const balances = await Promise.all(
          walletList.map((w) => getBalances(w.id).catch(() => null))
        )
        if (cancelled) return
        const total = balances
          .filter(Boolean)
          .flatMap((b) => b.balances)
          .filter((t) => t.symbol === 'USDC')
          .reduce((sum, t) => sum + Number(t.amount || 0), 0)
        setTotalUsdc(total)
      } catch (e) {
        if (!cancelled) setError(e.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div>
      <div className="page-header flex-between">
        <div>
          <h1>Dashboard</h1>
          <p>Balances and recent activity across your Circle-managed wallets on Ethereum Sepolia.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="grid-3" style={{ marginBottom: 24 }}>
        <div className="card stat-card">
          <div className="stat-label">Wallets</div>
          <div className="stat-value">{loading ? '—' : wallets.length}</div>
        </div>
        <div className="card stat-card">
          <div className="stat-label">Total USDC balance</div>
          <div className="stat-value">
            {loading || totalUsdc === null ? '—' : formatUsdc(totalUsdc)}
            <span className="unit">USDC</span>
          </div>
        </div>
        <div className="card stat-card">
          <div className="stat-label">Circle Mint mode</div>
          <div className="stat-value" style={{ fontSize: 20 }}>
            {loading ? '—' : mintLive ? 'Live' : 'Simulated'}
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="stack">
          <div className="ledger-tape">
            <div className="ledger-tape-label">Recent activity</div>
            {loading && <div className="ledger-tape-empty">Loading…</div>}
            {!loading && transactions.length === 0 && (
              <div className="ledger-tape-empty">No transactions yet. Fund a wallet from the faucet to begin.</div>
            )}
            {transactions.map((tx) => (
              <div className="ledger-tape-row" key={tx.id}>
                <span>
                  {tx.type} {tx.direction === 'OUT' ? '→' : '←'}{' '}
                  {tx.counterparty ? shortAddress(tx.counterparty) : ''}
                </span>
                <span>
                  {tx.direction === 'OUT' ? '−' : '+'}
                  {formatUsdc(tx.amountUsdc)} USDC
                </span>
              </div>
            ))}
          </div>

          <div className="card card-pad">
            <h3 style={{ marginBottom: 12 }}>Quick actions</h3>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <Link className="btn btn-primary" to="/wallets">
                Create a wallet
              </Link>
              <Link className="btn btn-ghost" to="/buy-sell">
                Buy USDC
              </Link>
              <Link className="btn btn-ghost" to="/transfers">
                Send USDC
              </Link>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <h2>Wallets</h2>
            <Link to="/wallets" className="muted" style={{ fontSize: 13 }}>
              Manage →
            </Link>
          </div>
          {loading && <div className="empty-state">Loading…</div>}
          {!loading && wallets.length === 0 && (
            <div className="empty-state">
              <div className="title">No wallets yet</div>
              <p className="muted">Create a developer-controlled wallet to get started.</p>
              <div className="spacer-sm" />
              <Link className="btn btn-primary btn-sm" to="/wallets">
                Create a wallet
              </Link>
            </div>
          )}
          {!loading && wallets.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Label</th>
                  <th>Address</th>
                  <th>Chain</th>
                </tr>
              </thead>
              <tbody>
                {wallets.map((w) => (
                  <tr key={w.id}>
                    <td>{w.label}</td>
                    <td className="mono">{shortAddress(w.address)}</td>
                    <td className="faint">{w.blockchain}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}
