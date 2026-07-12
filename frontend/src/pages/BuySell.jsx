import React, { useEffect, useState } from 'react'
import { listWallets } from '../api/wallets'
import { getWireInstructions, buyUsdc, sellUsdc, listMintOrders } from '../api/mint'
import StatusPill from '../components/StatusPill.jsx'
import { formatUsdc, formatDate } from '../utils/format.js'

export default function BuySell() {
  const [tab, setTab] = useState('buy')
  const [wallets, setWallets] = useState([])
  const [walletId, setWalletId] = useState('')
  const [amount, setAmount] = useState('')
  const [bankAccountId, setBankAccountId] = useState('')
  const [wireInfo, setWireInfo] = useState(null)
  const [orders, setOrders] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  async function loadAll() {
    const [walletList, wire, orderList] = await Promise.all([
      listWallets(),
      getWireInstructions(),
      listMintOrders()
    ])
    setWallets(walletList)
    if (walletList.length > 0) setWalletId((prev) => prev || walletList[0].id)
    setWireInfo(wire)
    setOrders(orderList)
  }

  useEffect(() => {
    loadAll().catch((e) => setError(e.message))
  }, [])

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setResult(null)
    try {
      const payload = { walletId, amountUsd: Number(amount) }
      const order =
        tab === 'buy' ? await buyUsdc(payload) : await sellUsdc({ ...payload, bankAccountId })
      setResult(order)
      setAmount('')
      const orderList = await listMintOrders()
      setOrders(orderList)
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Buy / Sell USDC</h1>
        <p>Fiat on- and off-ramp via Circle Mint. Buys settle by bank wire; sells pay out to a linked bank account.</p>
      </div>

      {!wireInfo?.live && (
        <div className="alert alert-note">
          Running in simulated Mint mode — this account isn't connected to an approved Circle Mint business
          account yet. Orders below complete instantly for demo purposes; no real money moves. Set{' '}
          <code>CIRCLE_MINT_LIVE=true</code> once your Circle account has bank linkage approved.
        </div>
      )}

      {error && <div className="alert alert-error">{error}</div>}

      <div className="grid-2">
        <div className="card card-pad">
          <div className="form-tabs">
            <button className={`form-tab ${tab === 'buy' ? 'active' : ''}`} onClick={() => setTab('buy')}>
              Buy USDC
            </button>
            <button className={`form-tab ${tab === 'sell' ? 'active' : ''}`} onClick={() => setTab('sell')}>
              Sell USDC
            </button>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="field">
              <label htmlFor="wallet-select">Wallet</label>
              <select id="wallet-select" value={walletId} onChange={(e) => setWalletId(e.target.value)} required>
                <option value="" disabled>
                  Select a wallet
                </option>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="amount">Amount (USD)</label>
              <input
                id="amount"
                type="number"
                min="1"
                step="0.01"
                placeholder="1000.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
              />
            </div>

            {tab === 'sell' && (
              <div className="field">
                <label htmlFor="bank-account">Linked bank account ID</label>
                <input
                  id="bank-account"
                  placeholder="Circle bank account ID"
                  value={bankAccountId}
                  onChange={(e) => setBankAccountId(e.target.value)}
                  required
                />
                <div className="help-text">Found under Bank Accounts in your Circle Dashboard.</div>
              </div>
            )}

            <button className={`btn ${tab === 'buy' ? 'btn-primary' : 'btn-brick'}`} type="submit" disabled={submitting}>
              {submitting ? 'Submitting…' : tab === 'buy' ? 'Start buy order' : 'Start sell order'}
            </button>
          </form>

          {result && (
            <div className="alert alert-info" style={{ marginTop: 18, marginBottom: 0 }}>
              Order <strong>{result.referenceId}</strong> created — status: {result.status}.{' '}
              {result.notes}
            </div>
          )}
        </div>

        <div className="card card-pad">
          <h3 style={{ marginBottom: 6 }}>Wire instructions</h3>
          <p className="help-text" style={{ marginBottom: 16 }}>
            {wireInfo?.live ? 'Live Circle Mint bank account.' : 'Simulated — for demo purposes only.'}
          </p>
          {wireInfo && (
            <table>
              <tbody>
                <tr>
                  <td className="faint">Beneficiary</td>
                  <td>{wireInfo.beneficiaryName}</td>
                </tr>
                <tr>
                  <td className="faint">Bank</td>
                  <td>{wireInfo.bankName}</td>
                </tr>
                <tr>
                  <td className="faint">Account #</td>
                  <td className="mono">{wireInfo.accountNumber}</td>
                </tr>
                <tr>
                  <td className="faint">Routing #</td>
                  <td className="mono">{wireInfo.routingNumber}</td>
                </tr>
                {wireInfo.swiftCode && (
                  <tr>
                    <td className="faint">SWIFT</td>
                    <td className="mono">{wireInfo.swiftCode}</td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
          <p className="help-text" style={{ marginTop: 14 }}>{wireInfo?.referenceInstructions}</p>
        </div>
      </div>

      <div className="spacer-md" />

      <div className="card">
        <div className="card-header">
          <h2>Order history</h2>
        </div>
        {orders.length === 0 && <div className="empty-state">No buy or sell orders yet.</div>}
        {orders.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Reference</th>
                <th>Type</th>
                <th className="num">Amount (USD)</th>
                <th>Status</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td className="mono">{o.referenceId}</td>
                  <td>{o.type}</td>
                  <td className="num">{formatUsdc(o.amountUsd)}</td>
                  <td>
                    <StatusPill state={o.status} simulated={!o.live} />
                  </td>
                  <td className="faint">{formatDate(o.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
