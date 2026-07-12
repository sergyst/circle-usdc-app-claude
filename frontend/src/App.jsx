import React from 'react'
import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Wallets from './pages/Wallets.jsx'
import BuySell from './pages/BuySell.jsx'
import Transfers from './pages/Transfers.jsx'
import History from './pages/History.jsx'

export default function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/wallets" element={<Wallets />} />
        <Route path="/buy-sell" element={<BuySell />} />
        <Route path="/transfers" element={<Transfers />} />
        <Route path="/history" element={<History />} />
      </Routes>
    </Layout>
  )
}
