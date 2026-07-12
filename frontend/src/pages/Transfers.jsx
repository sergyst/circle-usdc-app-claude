import React, { useEffect, useState } from 'react'
import { listWallets, getBalances } from '../api/wallets'
import { sendTransfer, refreshTransfer } from '../api/transfers'
import StatusPill from '../components/StatusPill.jsx'
import { formatUsdc } from '../utils/format.js'

export default function Transfers() {
  const [wallets, setWallets] = useState([])
  const [fromWalletId, setFromWalletId] = useState('')
  const [destinationAddress, setDestinationAddress] = useState('')
  const [amountUsdc, setAmountUsdc] = useState('')
  const [availableBalance, setAvailableBalance] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    listWallets()
      .then((list) => {
        setWallets(list)
        if (list.length > 0) setFromWalletId(list[0].id)
      })
      .catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    if (!fromWalletId) return
    getBalances(fromWalletId)
      .then((b) => {
        const usdc = b.balances.find((t) => t.symbol === 'USDC')
        setAvailableBalance(usdc ? usdc.amount : '0')
      })
      .catch(() => setAvailableBalance(null))
  }, [fromWalletId])

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setResult(null)
    try {
      const tx = await sendTransfer({
        fromWalletId,
        destinationAddress,
        amountUsdc: Number(amountUsdc)
      })
      setResult(tx)
      setDestinationAddress('')
      setAmountUsdc('')
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRefresh() {
    if (!result) return
    setRefreshing(true)
    try {
      const updated = await refreshTransfer(result.id)
      setResult(updated)
    } catch (e) {
      setError(e.message)
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Send USDC</h1>
        <p>On-chain transfer from a Circle-managed wallet to any Ethereum Sepolia address.</p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="grid-2">
        <div className="card card-pad">
          <form onSubmit={handleSubmit}>
            <div className="field">
              <label htmlFor="from-wallet">From wallet</label>
              <select id="from-wallet" value={fromWalletId} onChange={(e) => setFromWalletId(e.target.value)} required>
                <option value="" disabled>
                  Select a wallet
                </option>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.label}
                  </option>
                ))}
              </select>
              {availableBalance !== null && (
                <div className="help-text">Available: {formatUsdc(availableBalance)} USDC</div>
              )}
            </div>

            <div className="field">
              <label htmlFor="destination">Destination address</label>
              <input
                id="destination"
                className="mono"
                placeholder="0x…"
                value={destinationAddress}
                onChange={(e) => setDestinationAddress(e.target.value)}
                required
              />
            </div>

            <div className="field">
              <label htmlFor="transfer-amount">Amount (USDC)</label>
              <input
                id="transfer-amount"
                type="number"
                min="0.000001"
                step="0.000001"
                placeholder="10.00"
                value={amountUsdc}
                onChange={(e) => setAmountUsdc(e.target.value)}
                required
              />
            </div>

            <button className="btn btn-primary" type="submit" disabled={submitting || wallets.length === 0}>
              {submitting ? 'Sending…' : 'Send USDC'}
            </button>
          </form>
        </div>

        <div className="card card-pad">
          <h3 style={{ marginBottom: 14 }}>Transaction</h3>
          {!result && <p className="muted">Submit a transfer to see its status here.</p>}
          {result && (
            <div className="stack" style={{ gap: 10 }}>
              <div className="flex-between">
                <span className="faint">Status</span>
                <StatusPill state={result.state} />
              </div>
              <div className="flex-between">
                <span className="faint">Amount</span>
                <span className="mono">{formatUsdc(result.amountUsdc)} USDC</span>
              </div>
              <div className="flex-between">
                <span className="faint">To</span>
                <span className="mono">{result.counterparty}</span>
              </div>
              {result.txHash && (
                <div className="flex-between">
                  <span className="faint">Tx hash</span>
                  <span className="mono">{result.txHash}</span>
                </div>
              )}
              <button className="btn btn-ghost btn-sm" onClick={handleRefresh} disabled={refreshing} style={{ alignSelf: 'flex-start' }}>
                {refreshing ? 'Checking…' : 'Refresh status'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
