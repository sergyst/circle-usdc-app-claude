import React, { useEffect, useState } from 'react'
import { listWallets, createWallet, getBalances, requestFaucet } from '../api/wallets'
import { shortAddress } from '../utils/format.js'

export default function Wallets() {
  const [wallets, setWallets] = useState([])
  const [balancesByWallet, setBalancesByWallet] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [label, setLabel] = useState('')
  const [creating, setCreating] = useState(false)
  const [faucetingId, setFaucetingId] = useState(null)
  const [notice, setNotice] = useState(null)

  async function loadWallets() {
    setLoading(true)
    setError(null)
    try {
      const list = await listWallets()
      setWallets(list)
      const entries = await Promise.all(
        list.map(async (w) => {
          try {
            const b = await getBalances(w.id)
            return [w.id, b.balances]
          } catch {
            return [w.id, null]
          }
        })
      )
      setBalancesByWallet(Object.fromEntries(entries))
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadWallets()
  }, [])

  async function handleCreate(e) {
    e.preventDefault()
    if (!label.trim()) return
    setCreating(true)
    setError(null)
    try {
      await createWallet(label.trim())
      setLabel('')
      await loadWallets()
    } catch (e) {
      setError(e.message)
    } finally {
      setCreating(false)
    }
  }

  async function handleFaucet(walletId) {
    setFaucetingId(walletId)
    setNotice(null)
    setError(null)
    try {
      await requestFaucet(walletId)
      setNotice('Testnet USDC and ETH requested. Balances usually update within a minute — refresh below.')
    } catch (e) {
      setError(e.message)
    } finally {
      setFaucetingId(null)
    }
  }

  function balanceFor(walletId, symbol) {
    const balances = balancesByWallet[walletId]
    if (!balances) return null
    const entry = balances.find((b) => b.symbol === symbol)
    return entry ? entry.amount : '0'
  }

  return (
    <div>
      <div className="page-header">
        <h1>Wallets</h1>
        <p>Developer-controlled wallets, created and custodied through Circle's MPC infrastructure on Ethereum Sepolia.</p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-info">{notice}</div>}

      <div className="card card-pad" style={{ marginBottom: 24 }}>
        <h3 style={{ marginBottom: 14 }}>Create a wallet</h3>
        <form onSubmit={handleCreate} style={{ display: 'flex', gap: 10, alignItems: 'flex-end' }}>
          <div className="field" style={{ flex: 1, marginBottom: 0 }}>
            <label htmlFor="wallet-label">Label</label>
            <input
              id="wallet-label"
              placeholder="e.g. Treasury operating wallet"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
            />
          </div>
          <button className="btn btn-primary" type="submit" disabled={creating}>
            {creating ? 'Creating…' : 'Create wallet'}
          </button>
        </form>
      </div>

      <div className="card">
        <div className="card-header">
          <h2>All wallets</h2>
          <button className="btn btn-ghost btn-sm" onClick={loadWallets} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>

        {!loading && wallets.length === 0 && (
          <div className="empty-state">
            <div className="title">No wallets yet</div>
            <p className="muted">Create your first wallet above to get an Ethereum Sepolia address.</p>
          </div>
        )}

        {wallets.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Label</th>
                <th>Address</th>
                <th>Type</th>
                <th className="num">USDC</th>
                <th className="num">ETH</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {wallets.map((w) => (
                <tr key={w.id}>
                  <td>{w.label}</td>
                  <td className="mono" title={w.address}>
                    {shortAddress(w.address)}
                  </td>
                  <td className="faint">{w.accountType}</td>
                  <td className="num">{balanceFor(w.id, 'USDC') ?? '—'}</td>
                  <td className="num">{balanceFor(w.id, 'ETH') ?? '—'}</td>
                  <td>
                    <button
                      className="btn btn-ghost btn-sm"
                      onClick={() => handleFaucet(w.id)}
                      disabled={faucetingId === w.id}
                    >
                      {faucetingId === w.id ? 'Requesting…' : 'Fund from faucet'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
