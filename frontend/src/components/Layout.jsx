import React from 'react'
import { NavLink } from 'react-router-dom'

const links = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/wallets', label: 'Wallets' },
  { to: '/buy-sell', label: 'Buy / Sell' },
  { to: '/transfers', label: 'Send USDC' },
  { to: '/history', label: 'Ledger history' }
]

export default function Layout({ children }) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="mark">Ledger</span>
        </div>
        <div className="sidebar-sub">USDC on Ethereum · via Circle</div>

        <nav>
          <ul className="nav-list">
            {links.map((link) => (
              <li key={link.to}>
                <NavLink
                  to={link.to}
                  end={link.end}
                  className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}
                >
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="sidebar-footer">
          <span className="env-badge">
            <span className="dot" />
            Sepolia testnet
          </span>
        </div>
      </aside>

      <main className="main">{children}</main>
    </div>
  )
}
