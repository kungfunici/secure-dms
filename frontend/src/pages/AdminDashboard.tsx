import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/contexts/AuthContext'
import { useDarkMode } from '@/hooks/useDarkMode'
import { admin, webhooksApi, type AdminStatsResponse, type AdminUserResponse, type AuditLogResponse, type RetentionPolicyResponse, type LegalHoldResponse, type WebhookResponse, users } from '@/lib/api'
import Spinner from '@/components/ui/spinner'
import {
  LayoutDashboard, Users as UsersIcon, FileText, Activity, Shield, ShieldOff,
  Search, X, Sun, Moon, LogOut, Eye, Clock, Lock, Settings, Globe,
  Plus, Pencil, Trash2, Check, AlertTriangle
} from 'lucide-react'

type AdminTab = 'dashboard' | 'users' | 'audit-logs' | 'retention' | 'legal-holds' | 'system' | 'webhooks'

export default function AdminDashboard() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [dark, toggleDark] = useDarkMode()
  const [activeTab, setActiveTab] = useState<AdminTab>('dashboard')
  const [error, setError] = useState('')

  const [stats, setStats] = useState<AdminStatsResponse | null>(null)

  const [userList, setUserList] = useState<AdminUserResponse[]>([])
  const [userPage, setUserPage] = useState(0)
  const [userTotalPages, setUserTotalPages] = useState(0)

  const [logList, setLogList] = useState<AuditLogResponse[]>([])
  const [logPage, setLogPage] = useState(0)
  const [logTotalPages, setLogTotalPages] = useState(0)
  const [logActionFilter, setLogActionFilter] = useState('')
  const [logUserFilter, setLogUserFilter] = useState('')

  const [policies, setPolicies] = useState<RetentionPolicyResponse[]>([])
  const [policyPage, setPolicyPage] = useState(0)
  const [policyTotalPages, setPolicyTotalPages] = useState(0)
  const [showPolicyForm, setShowPolicyForm] = useState(false)
  const [editPolicy, setEditPolicy] = useState<RetentionPolicyResponse | null>(null)
  const [policyForm, setPolicyForm] = useState({ name: '', documentType: '', folderId: '', retentionDays: 30, action: 'DELETE', enabled: true })

  const [legalHolds, setLegalHolds] = useState<LegalHoldResponse[]>([])
  const [legalHoldPage, setLegalHoldPage] = useState(0)
  const [legalHoldTotalPages, setLegalHoldTotalPages] = useState(0)
  const [showLegalHoldForm, setShowLegalHoldForm] = useState(false)
  const [legalHoldDocId, setLegalHoldDocId] = useState('')
  const [legalHoldReason, setLegalHoldReason] = useState('')

  const [sysConfig, setSysConfig] = useState<Record<string, string>>({})
  const [editingConfig, setEditingConfig] = useState<Record<string, string>>({})
  const [configDirty, setConfigDirty] = useState(false)

  const [webhookList, setWebhookList] = useState<WebhookResponse[]>([])
  const [webhookPage, setWebhookPage] = useState(0)
  const [webhookTotalPages, setWebhookTotalPages] = useState(0)
  const [showWebhookForm, setShowWebhookForm] = useState(false)
  const [editWebhook, setEditWebhook] = useState<WebhookResponse | null>(null)
  const [webhookForm, setWebhookForm] = useState({ url: '', events: [] as string[], secret: '', enabled: true })
  const AVAILABLE_EVENTS = ['UPLOAD', 'UPDATE', 'DELETE', 'TRASH', 'RESTORE']

  useEffect(() => {
    if (user?.role !== 'ADMIN') {
      navigate('/', { replace: true })
      return
    }
    loadStats()
    loadUsers(0)
    loadLogs(0)
    loadPolicies(0)
    loadLegalHolds(0)
    loadConfig()
    loadWebhooks(0)
  }, [])

  async function loadStats() {
    try { setStats(await admin.getStats()) } catch { setError('Failed to load stats') }
  }

  async function loadUsers(page: number) {
    setUserPage(page)
    try {
      const res = await admin.getUsers(page, 50)
      setUserList(res.content)
      setUserTotalPages(res.totalPages)
    } catch { setError('Failed to load users') }
  }

  async function loadLogs(page: number) {
    setLogPage(page)
    try {
      const res = await admin.getAuditLogs({
        page, size: 50,
        action: logActionFilter || undefined,
        username: logUserFilter || undefined,
      })
      setLogList(res.content)
      setLogTotalPages(res.totalPages)
    } catch { setError('Failed to load audit logs') }
  }

  async function handleRoleChange(userId: number, role: string) {
    try {
      const updated = await admin.updateUserRole(userId, role)
      setUserList(prev => prev.map(u => u.id === userId ? { ...u, role: updated.role } : u))
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to update role') }
  }

  async function handleToggleEnabled(userId: number, current: boolean) {
    try {
      const updated = await admin.setUserEnabled(userId, !current)
      setUserList(prev => prev.map(u => u.id === userId ? { ...u, enabled: updated.enabled } : u))
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to update user') }
  }

  async function loadPolicies(page: number) {
    setPolicyPage(page)
    try {
      const res = await admin.getRetentionPolicies(page, 50)
      setPolicies(res.content)
      setPolicyTotalPages(res.totalPages)
    } catch { setError('Failed to load retention policies') }
  }

  function openPolicyForm(policy?: RetentionPolicyResponse) {
    if (policy) {
      setEditPolicy(policy)
      setPolicyForm({
        name: policy.name,
        documentType: policy.documentType || '',
        folderId: policy.folderId?.toString() || '',
        retentionDays: policy.retentionDays,
        action: policy.action,
        enabled: policy.enabled,
      })
    } else {
      setEditPolicy(null)
      setPolicyForm({ name: '', documentType: '', folderId: '', retentionDays: 30, action: 'DELETE', enabled: true })
    }
    setShowPolicyForm(true)
  }

  async function handleSavePolicy() {
    if (!policyForm.name.trim() || !policyForm.retentionDays) return
    try {
      const data = {
        name: policyForm.name,
        documentType: policyForm.documentType || undefined,
        folderId: policyForm.folderId ? Number(policyForm.folderId) : undefined,
        retentionDays: policyForm.retentionDays,
        action: policyForm.action,
        enabled: policyForm.enabled,
      }
      if (editPolicy) {
        await admin.updateRetentionPolicy(editPolicy.id, data)
      } else {
        await admin.createRetentionPolicy(data)
      }
      setShowPolicyForm(false)
      loadPolicies(0)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to save policy') }
  }

  async function handleDeletePolicy(id: number) {
    if (!confirm('Delete this retention policy?')) return
    try {
      await admin.deleteRetentionPolicy(id)
      loadPolicies(0)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to delete policy') }
  }

  async function loadLegalHolds(page: number) {
    setLegalHoldPage(page)
    try {
      const res = await admin.getLegalHolds(page, 50)
      setLegalHolds(res.content)
      setLegalHoldTotalPages(res.totalPages)
    } catch { setError('Failed to load legal holds') }
  }

  async function handleCreateLegalHold() {
    if (!legalHoldDocId.trim() || !legalHoldReason.trim()) return
    try {
      await admin.createLegalHold(Number(legalHoldDocId), legalHoldReason)
      setShowLegalHoldForm(false)
      setLegalHoldDocId('')
      setLegalHoldReason('')
      loadLegalHolds(0)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to create legal hold') }
  }

  async function handleReleaseLegalHold(id: number) {
    if (!confirm('Release this legal hold?')) return
    try {
      await admin.releaseLegalHold(id)
      loadLegalHolds(0)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to release legal hold') }
  }

  async function loadConfig() {
    try {
      const cfg = await admin.getSystemConfig()
      setSysConfig(cfg)
      setEditingConfig({ ...cfg })
    } catch { setError('Failed to load system config') }
  }

  async function handleSaveConfig() {
    try {
      const updated = await admin.updateSystemConfig(editingConfig)
      setSysConfig(updated)
      setEditingConfig({ ...updated })
      setConfigDirty(false)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to save config') }
  }

  async function loadWebhooks(page: number) {
    setWebhookPage(page)
    try {
      const res = await webhooksApi.list(page, 50)
      setWebhookList(res.content)
      setWebhookTotalPages(res.totalPages)
    } catch { setError('Failed to load webhooks') }
  }

  function openWebhookForm(hook?: WebhookResponse) {
    if (hook) {
      setEditWebhook(hook)
      setWebhookForm({ url: hook.url, events: hook.events, secret: hook.secret || '', enabled: hook.enabled })
    } else {
      setEditWebhook(null)
      setWebhookForm({ url: '', events: [], secret: '', enabled: true })
    }
    setShowWebhookForm(true)
  }

  async function handleSaveWebhook() {
    if (!webhookForm.url.trim()) return
    try {
      const data = { url: webhookForm.url, events: webhookForm.events, secret: webhookForm.secret || undefined, enabled: webhookForm.enabled }
      if (editWebhook) {
        await webhooksApi.update(editWebhook.id, data)
      } else {
        await webhooksApi.create(data)
      }
      setShowWebhookForm(false)
      loadWebhooks(0)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to save webhook') }
  }

  async function handleDeleteWebhook(id: number) {
    if (!confirm('Delete this webhook?')) return
    try {
      await webhooksApi.delete(id)
      loadWebhooks(0)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to delete webhook') }
  }

  function toggleWebhookEvent(event: string) {
    setWebhookForm(f => ({
      ...f,
      events: f.events.includes(event) ? f.events.filter(e => e !== event) : [...f.events, event],
    }))
  }

  function tabClasses(tab: AdminTab) {
    return `px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
      activeTab === tab
        ? 'bg-background border border-b-0 border-border text-foreground'
        : 'text-muted-foreground hover:text-foreground cursor-pointer'
    }`
  }

  const pageBtn = (page: number, total: number, go: (p: number) => void) => (
    <div className="flex items-center gap-2">
      <Button size="sm" variant="outline" disabled={page <= 0} onClick={() => go(page - 1)}>Previous</Button>
      <span className="text-xs text-muted-foreground">Page {page + 1} of {Math.max(1, total)}</span>
      <Button size="sm" variant="outline" disabled={page >= total - 1} onClick={() => go(page + 1)}>Next</Button>
    </div>
  )

  const StorageChart = () => {
    if (!stats) return null
    const maxVal = Math.max(stats.userCount, stats.documentCount, stats.auditLogCount24h, 1)
    const bars = [
      { label: 'Users', value: stats.userCount, color: 'bg-blue-500' },
      { label: 'Documents', value: stats.documentCount, color: 'bg-violet-500' },
      { label: 'Activities (24h)', value: stats.auditLogCount24h, color: 'bg-amber-500' },
    ]
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm text-muted-foreground">Overview</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-end gap-4 h-32">
            {bars.map(bar => (
              <div key={bar.label} className="flex-1 flex flex-col items-center gap-1">
                <span className="text-xs font-medium">{bar.value}</span>
                <div
                  className={`w-full rounded-t ${bar.color} transition-all`}
                  style={{ height: `${(bar.value / maxVal) * 100}%`, minHeight: bar.value > 0 ? '4px' : '0' }}
                />
                <span className="text-[10px] text-muted-foreground text-center">{bar.label}</span>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="min-h-screen bg-muted/30 dark:bg-muted/10">
      <header className="border-b bg-background">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <LayoutDashboard className="h-6 w-6 text-primary" />
            <span className="font-semibold">Admin Dashboard</span>
          </div>
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={toggleDark} title={dark ? 'Light mode' : 'Dark mode'}>
              {dark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
            </Button>
            <Button variant="ghost" size="sm" onClick={() => navigate('/')}>
              <Eye className="h-4 w-4 mr-2" /> Back to DMS
            </Button>
            <button onClick={() => navigate(`/profile/${user?.id}`)} className="flex items-center gap-2 hover:opacity-80 transition-opacity">
              <img src={users.avatarUrl(user?.id ?? 0)} alt="" className="w-8 h-8 rounded-full object-cover border border-border"
                onError={(e) => { (e.target as HTMLImageElement).src = '/default-avatar.svg' }} />
              <span className="text-sm text-muted-foreground hidden sm:inline">{user?.username}</span>
            </button>
            <Button variant="ghost" size="sm" onClick={logout}><LogOut className="h-4 w-4 mr-2" /> Logout</Button>
          </div>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 py-6 space-y-6">
        {error && (
          <div className="bg-destructive/10 text-destructive text-sm p-3 rounded-md">
            {error}
            <button className="ml-2 font-bold" onClick={() => setError('')}>×</button>
          </div>
        )}

        <Card>
          <CardHeader className="pb-0">
            <div className="flex items-center gap-1 flex-wrap">
              {(['dashboard', 'users', 'audit-logs', 'retention', 'legal-holds', 'system', 'webhooks'] as AdminTab[]).map(tab => (
                <button key={tab} className={tabClasses(tab)} onClick={() => setActiveTab(tab)}>
                  {tab === 'dashboard' && <LayoutDashboard className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'users' && <UsersIcon className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'audit-logs' && <Activity className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'retention' && <Clock className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'legal-holds' && <Lock className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'system' && <Settings className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'webhooks' && <Globe className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'dashboard' ? 'Dashboard' : tab === 'users' ? 'Users' : tab === 'audit-logs' ? 'Audit Logs'
                    : tab === 'retention' ? 'Retention' : tab === 'legal-holds' ? 'Legal Holds'
                    : tab === 'system' ? 'System' : 'Webhooks'}
                </button>
              ))}
            </div>
          </CardHeader>
          <CardContent className="pt-4">

            {/* ========================= DASHBOARD ========================= */}
            {activeTab === 'dashboard' && (
              stats ? (
                <div className="space-y-4">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                    <Card>
                      <CardHeader className="pb-2">
                        <CardTitle className="text-sm text-muted-foreground flex items-center gap-2">
                          <UsersIcon className="h-4 w-4" /> Users
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-3xl font-bold">{stats.userCount}</p>
                      </CardContent>
                    </Card>
                    <Card>
                      <CardHeader className="pb-2">
                        <CardTitle className="text-sm text-muted-foreground flex items-center gap-2">
                          <FileText className="h-4 w-4" /> Documents
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-3xl font-bold">{stats.documentCount}</p>
                      </CardContent>
                    </Card>
                    <Card>
                      <CardHeader className="pb-2">
                        <CardTitle className="text-sm text-muted-foreground flex items-center gap-2">
                          <Activity className="h-4 w-4" /> Activities (24h)
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-3xl font-bold">{stats.auditLogCount24h}</p>
                      </CardContent>
                    </Card>
                  </div>
                  <StorageChart />
                </div>
              ) : (
                <div className="flex items-center justify-center py-12 text-muted-foreground">
                  <Spinner className="h-6 w-6 mr-2" /> <span className="text-sm">Loading...</span>
                </div>
              )
            )}

            {/* ========================= USERS ========================= */}
            {activeTab === 'users' && (
              <div className="space-y-4">
                {pageBtn(userPage, userTotalPages, loadUsers)}
                <div className="divide-y border rounded-lg">
                  {userList.map(u => (
                    <div key={u.id} className="flex items-center justify-between py-3 px-4 gap-4">
                      <div className="min-w-0 flex-1 flex items-center gap-3">
                        <img src={users.avatarUrl(u.id)} alt="" className="w-8 h-8 rounded-full object-cover border"
                          onError={(e) => { (e.target as HTMLImageElement).src = '/default-avatar.svg' }} />
                        <div>
                          <p className="text-sm font-medium">{u.username}</p>
                          <p className="text-xs text-muted-foreground">{u.email}</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className={`text-xs font-medium px-2 py-0.5 rounded ${
                          u.enabled
                            ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                            : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
                        }`}>
                          {u.enabled ? 'Active' : 'Disabled'}
                        </span>
                        <select
                          className="h-8 rounded-md border border-input bg-background px-2 text-xs"
                          value={u.role}
                          onChange={e => handleRoleChange(u.id, e.target.value)}>
                          <option value="USER">User</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                        <Button variant="ghost" size="icon" className="h-7 w-7"
                          onClick={() => handleToggleEnabled(u.id, u.enabled)}
                          title={u.enabled ? 'Disable user' : 'Enable user'}>
                          {u.enabled ? <Shield className="h-3.5 w-3.5" /> : <ShieldOff className="h-3.5 w-3.5" />}
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* ========================= AUDIT LOGS ========================= */}
            {activeTab === 'audit-logs' && (
              <div className="space-y-4">
                <div className="flex gap-2 flex-wrap">
                  <div className="flex-1 min-w-[150px]">
                    <Input placeholder="Filter by action..." value={logActionFilter}
                      onChange={e => setLogActionFilter(e.target.value)} />
                  </div>
                  <div className="flex-1 min-w-[150px]">
                    <Input placeholder="Filter by username..." value={logUserFilter}
                      onChange={e => setLogUserFilter(e.target.value)} />
                  </div>
                  <Button variant="secondary" size="sm" onClick={() => loadLogs(0)}>
                    <Search className="h-4 w-4 mr-1" /> Filter
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => { setLogActionFilter(''); setLogUserFilter(''); loadLogs(0) }}>
                    <X className="h-4 w-4 mr-1" /> Clear
                  </Button>
                </div>
                {pageBtn(logPage, logTotalPages, loadLogs)}
                <div className="divide-y border rounded-lg max-h-[60vh] overflow-y-auto">
                  {logList.map(log => (
                    <div key={log.id} className="py-2.5 px-4 flex items-center justify-between gap-4">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium">{log.action}</p>
                        <p className="text-xs text-muted-foreground truncate">
                          {log.username && <span>by {log.username}</span>}
                          {log.documentId && <span> &middot; doc #{log.documentId}</span>}
                          {log.details && <span> &middot; {log.details}</span>}
                          {log.ipAddress && <span> &middot; {log.ipAddress}</span>}
                        </p>
                      </div>
                      <span className="text-xs text-muted-foreground shrink-0">
                        {new Date(log.timestamp).toLocaleString()}
                      </span>
                    </div>
                  ))}
                  {logList.length === 0 && (
                    <p className="text-sm text-muted-foreground text-center py-8">No audit logs found</p>
                  )}
                </div>
              </div>
            )}

            {/* ========================= RETENTION POLICIES ========================= */}
            {activeTab === 'retention' && (
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <p className="text-sm text-muted-foreground">Automatic document retention rules</p>
                  <Button size="sm" onClick={() => openPolicyForm()}>
                    <Plus className="h-4 w-4 mr-1" /> Add Policy
                  </Button>
                </div>

                {showPolicyForm && (
                  <Card>
                    <CardContent className="pt-4">
                      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                        <div>
                          <label className="text-xs font-medium mb-1 block">Name</label>
                          <Input value={policyForm.name} onChange={e => setPolicyForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g. Delete PDFs after 90 days" />
                        </div>
                        <div>
                          <label className="text-xs font-medium mb-1 block">Document Type</label>
                          <Input value={policyForm.documentType} onChange={e => setPolicyForm(f => ({ ...f, documentType: e.target.value }))} placeholder="e.g. PDF, Invoice" />
                        </div>
                        <div>
                          <label className="text-xs font-medium mb-1 block">Folder ID</label>
                          <Input value={policyForm.folderId} onChange={e => setPolicyForm(f => ({ ...f, folderId: e.target.value }))} placeholder="Optional" />
                        </div>
                        <div>
                          <label className="text-xs font-medium mb-1 block">Retention Days</label>
                          <Input type="number" value={policyForm.retentionDays} onChange={e => setPolicyForm(f => ({ ...f, retentionDays: Number(e.target.value) }))} />
                        </div>
                        <div>
                          <label className="text-xs font-medium mb-1 block">Action</label>
                          <select className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                            value={policyForm.action}
                            onChange={e => setPolicyForm(f => ({ ...f, action: e.target.value }))}>
                            <option value="DELETE">Delete</option>
                            <option value="ARCHIVE">Archive (move to trash)</option>
                          </select>
                        </div>
                        <div className="flex items-end gap-2">
                          <label className="flex items-center gap-2 text-sm cursor-pointer">
                            <input type="checkbox" checked={policyForm.enabled}
                              onChange={e => setPolicyForm(f => ({ ...f, enabled: e.target.checked }))} />
                            Enabled
                          </label>
                        </div>
                      </div>
                      <div className="flex gap-2 mt-3">
                        <Button size="sm" onClick={handleSavePolicy} disabled={!policyForm.name.trim()}>
                          <Check className="h-4 w-4 mr-1" /> {editPolicy ? 'Update' : 'Create'}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setShowPolicyForm(false)}>Cancel</Button>
                      </div>
                    </CardContent>
                  </Card>
                )}

                {pageBtn(policyPage, policyTotalPages, loadPolicies)}
                <div className="divide-y border rounded-lg">
                  {policies.map(p => (
                    <div key={p.id} className="flex items-center justify-between py-3 px-4 gap-4">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium">{p.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {p.documentType && <span>{p.documentType} &middot; </span>}
                          {p.retentionDays} days &middot; {p.action === 'DELETE' ? 'Permanent delete' : 'Archive to trash'}
                          {p.folderName && <span> &middot; Folder: {p.folderName}</span>}
                        </p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <span className={`text-xs font-medium px-2 py-0.5 rounded ${
                          p.enabled ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' : 'bg-gray-100 text-gray-500'
                        }`}>
                          {p.enabled ? 'Active' : 'Disabled'}
                        </span>
                        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => openPolicyForm(p)} title="Edit">
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => handleDeletePolicy(p.id)} title="Delete">
                          <Trash2 className="h-3.5 w-3.5 text-destructive" />
                        </Button>
                      </div>
                    </div>
                  ))}
                  {policies.length === 0 && !showPolicyForm && (
                    <p className="text-sm text-muted-foreground text-center py-8">No retention policies defined</p>
                  )}
                </div>
              </div>
            )}

            {/* ========================= LEGAL HOLDS ========================= */}
            {activeTab === 'legal-holds' && (
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <p className="text-sm text-muted-foreground">Legal holds prevent document deletion</p>
                  <Button size="sm" onClick={() => setShowLegalHoldForm(true)}>
                    <Plus className="h-4 w-4 mr-1" /> Add Legal Hold
                  </Button>
                </div>

                {showLegalHoldForm && (
                  <Card>
                    <CardContent className="pt-4">
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div>
                          <label className="text-xs font-medium mb-1 block">Document ID</label>
                          <Input type="number" value={legalHoldDocId} onChange={e => setLegalHoldDocId(e.target.value)} placeholder="Document ID" />
                        </div>
                        <div>
                          <label className="text-xs font-medium mb-1 block">Reason</label>
                          <Input value={legalHoldReason} onChange={e => setLegalHoldReason(e.target.value)} placeholder="e.g. Litigation hold" />
                        </div>
                      </div>
                      <div className="flex gap-2 mt-3">
                        <Button size="sm" onClick={handleCreateLegalHold} disabled={!legalHoldDocId.trim() || !legalHoldReason.trim()}>
                          <Check className="h-4 w-4 mr-1" /> Create
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setShowLegalHoldForm(false)}>Cancel</Button>
                      </div>
                    </CardContent>
                  </Card>
                )}

                {pageBtn(legalHoldPage, legalHoldTotalPages, loadLegalHolds)}
                <div className="divide-y border rounded-lg">
                  {legalHolds.map(h => (
                    <div key={h.id} className="flex items-center justify-between py-3 px-4 gap-4">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium">{h.documentName}</p>
                        <p className="text-xs text-muted-foreground">
                          doc #{h.documentId} &middot; {h.reason}
                          {h.createdByUsername && <span> &middot; by {h.createdByUsername}</span>}
                          <span> &middot; {new Date(h.createdAt).toLocaleDateString()}</span>
                        </p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        {h.releasedAt ? (
                          <span className="text-xs font-medium px-2 py-0.5 rounded bg-gray-100 text-gray-500">
                            Released {new Date(h.releasedAt).toLocaleDateString()}
                          </span>
                        ) : (
                          <>
                            <span className="text-xs font-medium px-2 py-0.5 rounded bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400">
                              Active
                            </span>
                            <Button variant="ghost" size="sm" className="h-7 text-xs"
                              onClick={() => handleReleaseLegalHold(h.id)}>
                              <AlertTriangle className="h-3.5 w-3.5 mr-1" /> Release
                            </Button>
                          </>
                        )}
                      </div>
                    </div>
                  ))}
                  {legalHolds.length === 0 && !showLegalHoldForm && (
                    <p className="text-sm text-muted-foreground text-center py-8">No legal holds</p>
                  )}
                </div>
              </div>
            )}

            {/* ========================= SYSTEM CONFIG ========================= */}
            {activeTab === 'system' && (
              <div className="space-y-4">
                <p className="text-sm text-muted-foreground">System configuration values</p>
                <div className="divide-y border rounded-lg">
                  {Object.entries(editingConfig).map(([key, value]) => (
                    <div key={key} className="flex items-center gap-4 py-3 px-4">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium">{key}</p>
                      </div>
                      <div className="flex-1">
                        <Input value={value} onChange={e => {
                          setEditingConfig(prev => ({ ...prev, [key]: e.target.value }))
                          setConfigDirty(true)
                        }} />
                      </div>
                    </div>
                  ))}
                </div>
                {configDirty && (
                  <div className="flex gap-2">
                    <Button size="sm" onClick={handleSaveConfig}>
                      <Check className="h-4 w-4 mr-1" /> Save Changes
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => { setEditingConfig({ ...sysConfig }); setConfigDirty(false) }}>
                      Cancel
                    </Button>
                  </div>
                )}
              </div>
            )}

            {/* ========================= WEBHOOKS ========================= */}
            {activeTab === 'webhooks' && (
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <p className="text-sm text-muted-foreground">HTTP callbacks for document events</p>
                  <Button size="sm" onClick={() => openWebhookForm()}>
                    <Plus className="h-4 w-4 mr-1" /> Add Webhook
                  </Button>
                </div>

                {showWebhookForm && (
                  <Card>
                    <CardContent className="pt-4">
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="sm:col-span-2">
                          <label className="text-xs font-medium mb-1 block">URL</label>
                          <Input value={webhookForm.url} onChange={e => setWebhookForm(f => ({ ...f, url: e.target.value }))} placeholder="https://example.com/webhook" />
                        </div>
                        <div>
                          <label className="text-xs font-medium mb-1 block">Secret (for HMAC signature)</label>
                          <Input value={webhookForm.secret} onChange={e => setWebhookForm(f => ({ ...f, secret: e.target.value }))} placeholder="Optional shared secret" />
                        </div>
                        <div className="flex items-end">
                          <label className="flex items-center gap-2 text-sm cursor-pointer">
                            <input type="checkbox" checked={webhookForm.enabled}
                              onChange={e => setWebhookForm(f => ({ ...f, enabled: e.target.checked }))} />
                            Enabled
                          </label>
                        </div>
                      </div>
                      <div className="mt-3">
                        <label className="text-xs font-medium mb-1 block">Events</label>
                        <div className="flex flex-wrap gap-2">
                          {AVAILABLE_EVENTS.map(event => (
                            <button key={event}
                              className={`text-xs px-2.5 py-1 rounded-full border transition-colors ${
                                webhookForm.events.includes(event)
                                  ? 'bg-primary text-primary-foreground border-primary'
                                  : 'bg-background text-muted-foreground border-border hover:border-primary'
                              }`}
                              onClick={() => toggleWebhookEvent(event)}>
                              {event}
                            </button>
                          ))}
                        </div>
                      </div>
                      <div className="flex gap-2 mt-3">
                        <Button size="sm" onClick={handleSaveWebhook} disabled={!webhookForm.url.trim() || webhookForm.events.length === 0}>
                          <Check className="h-4 w-4 mr-1" /> {editWebhook ? 'Update' : 'Create'}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setShowWebhookForm(false)}>Cancel</Button>
                      </div>
                    </CardContent>
                  </Card>
                )}

                {pageBtn(webhookPage, webhookTotalPages, loadWebhooks)}
                <div className="divide-y border rounded-lg">
                  {webhookList.map(h => (
                    <div key={h.id} className="flex items-center justify-between py-3 px-4 gap-4">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium truncate">{h.url}</p>
                        <p className="text-xs text-muted-foreground">
                          {h.events.join(', ')}
                          {h.createdByUsername && <span> &middot; by {h.createdByUsername}</span>}
                        </p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <span className={`text-xs font-medium px-2 py-0.5 rounded ${
                          h.enabled ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' : 'bg-gray-100 text-gray-500'
                        }`}>
                          {h.enabled ? 'Active' : 'Disabled'}
                        </span>
                        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => openWebhookForm(h)} title="Edit">
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => handleDeleteWebhook(h.id)} title="Delete">
                          <Trash2 className="h-3.5 w-3.5 text-destructive" />
                        </Button>
                      </div>
                    </div>
                  ))}
                  {webhookList.length === 0 && !showWebhookForm && (
                    <p className="text-sm text-muted-foreground text-center py-8">No webhooks configured</p>
                  )}
                </div>
              </div>
            )}

          </CardContent>
        </Card>
      </main>
    </div>
  )
}
