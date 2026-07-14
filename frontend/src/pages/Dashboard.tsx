import { useState, useEffect, type DragEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/contexts/AuthContext'
import { useDarkMode } from '@/hooks/useDarkMode'
import { folders, documents, users, notifications as notifApi, type DocumentResponse, type FolderResponse, type PermissionResponse, type UserResponse, type NotificationResponse, type VersionResponse } from '@/lib/api'
import {
  FileText, LogOut, Search, Upload, Download, Trash2, FolderPlus, Folder, Share2, X, UserPlus, MoveRight,
  Moon, Sun, Bell, RotateCcw, Clock, FileInput, Archive, Eye, History, CheckSquare, Square, DownloadCloud, Pencil,
  Star, List, Grid3X3
} from 'lucide-react'

type DocTab = 'mine' | 'shared-with-me' | 'shared-by-me' | 'trash' | 'favorites'
type UploadMode = 'normal' | 'drop'

const DOC_TYPES = ['', 'PDF', 'Word', 'Excel', 'PowerPoint', 'Image', 'Text', 'CSV', 'Other'] as const

export default function Dashboard() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [dark, toggleDark] = useDarkMode()

  const [docs, setDocs] = useState<DocumentResponse[]>([])
  const [recentDocs, setRecentDocs] = useState<DocumentResponse[]>([])
  const [folderList, setFolderList] = useState<FolderResponse[]>([])
  const [activeTab, setActiveTab] = useState<DocTab>('mine')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedFolder, setSelectedFolder] = useState<number | undefined>(undefined)
  const [selectedDocType, setSelectedDocType] = useState('')
  const [error, setError] = useState('')
  const [showFolders, setShowFolders] = useState(false)
  const [newFolderName, setNewFolderName] = useState('')

  // Notifications
  const [notifList, setNotifList] = useState<NotificationResponse[]>([])
  const [notifCount, setNotifCount] = useState(0)
  const [showNotif, setShowNotif] = useState(false)

  // Upload
  const [file, setFile] = useState<File | null>(null)
  const [description, setDescription] = useState('')
  const [documentType, setDocumentType] = useState('')
  const [uploadMode, setUploadMode] = useState<UploadMode>('normal')
  const [dragOver, setDragOver] = useState(false)

  // Sharing
  const [shareDocId, setShareDocId] = useState<number | null>(null)
  const [shareSearch, setShareSearch] = useState('')
  const [shareResults, setShareResults] = useState<UserResponse[]>([])
  const [selectedUser, setSelectedUser] = useState<UserResponse | null>(null)
  const [shareType, setShareType] = useState<'READ' | 'WRITE'>('READ')
  const [permissions, setPermissions] = useState<PermissionResponse[]>([])

  // Move
  const [moveDocId, setMoveDocId] = useState<number | null>(null)

  // Preview
  const [previewDoc, setPreviewDoc] = useState<DocumentResponse | null>(null)
  const [previewBlobUrl, setPreviewBlobUrl] = useState<string | null>(null)

  // Versions
  const [versionDocId, setVersionDocId] = useState<number | null>(null)
  const [versions, setVersions] = useState<VersionResponse[]>([])

  // Editor
  const [editDoc, setEditDoc] = useState<DocumentResponse | null>(null)
  const [editContent, setEditContent] = useState('')
  const [editLoading, setEditLoading] = useState(false)

  // Batch selection
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [batchMoveFolderId, setBatchMoveFolderId] = useState<number | null>(null)

  // View mode + sorting
  const [listView, setListView] = useState(true)
  const [sortBy, setSortBy] = useState('uploadedAt')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc')

  // ---- Loaders ----
  async function loadDocs() {
    try {
      if (activeTab === 'favorites') {
        const favs = await documents.listFavorites()
        setDocs(favs)
        setSelectedIds(new Set())
        return
      }
      if (searchQuery) {
        const res = await documents.search(searchQuery)
        setDocs(res.content)
        setSelectedIds(new Set())
        return
      }
      if (activeTab === 'trash') {
        const res = await documents.trash(0, 50, searchQuery || undefined)
        setDocs(res.content)
        setSelectedIds(new Set())
        return
      }
      if (activeTab === 'mine') {
        const sort = sortDir === 'desc' ? `${sortBy},desc` : sortBy
        const res = await documents.list(0, 50, sort)
        let all = res.content
        if (selectedFolder !== undefined) all = all.filter(d => d.folderId === selectedFolder)
        if (selectedDocType) all = all.filter(d => d.documentType === selectedDocType)
        setDocs(all)
      } else if (activeTab === 'shared-with-me') {
        const res = await documents.sharedWithMe()
        setDocs(res.content)
      } else if (activeTab === 'shared-by-me') {
        const res = await documents.sharedByMe()
        setDocs(res.content)
      }
      setSelectedIds(new Set())
    } catch { setError('Failed to load documents') }
  }

  async function loadRecent() {
    try {
      const res = await documents.recentlyViewed()
      setRecentDocs(res.content)
    } catch { /* ignore */ }
  }

  async function loadFolders() {
    try { setFolderList(await folders.list()) } catch { /* ignore */ }
  }

  async function loadNotifications() {
    try {
      setNotifList(await notifApi.list())
      const { count } = await notifApi.unreadCount()
      setNotifCount(count)
    } catch { /* ignore */ }
  }

  useEffect(() => { loadDocs(); loadFolders(); loadRecent(); loadNotifications() }, [activeTab, selectedFolder, selectedDocType, sortBy, sortDir])

  // ---- Drag & Drop ----
  function onDragOver(e: DragEvent) { e.preventDefault(); setDragOver(true) }
  function onDragLeave() { setDragOver(false) }
  function onDrop(e: DragEvent) {
    e.preventDefault(); setDragOver(false); setUploadMode('drop')
    const f = e.dataTransfer.files?.[0]
    if (f) setFile(f)
  }

  // ---- Actions ----
  async function handleUpload(e: React.FormEvent) {
    e.preventDefault()
    if (!file) return
    setError('')
    try {
      await documents.upload(file, description, documentType || undefined, selectedFolder)
      setFile(null); setDescription(''); setDocumentType(''); setUploadMode('normal')
      loadDocs()
    } catch (err) { setError(err instanceof Error ? err.message : 'Upload failed') }
  }

  async function handleDownload(id: number) {
    try { await documents.download(id) } catch (err) { setError(err instanceof Error ? err.message : 'Download failed') }
  }

  async function handleDelete(id: number) {
    try { await documents.delete(id); loadDocs() } catch (err) { setError(err instanceof Error ? err.message : 'Delete failed') }
  }

  async function handleRestore(id: number) {
    try { await documents.restore(id); loadDocs() } catch (err) { setError(err instanceof Error ? err.message : 'Restore failed') }
  }

  async function handleCreateFolder() {
    if (!newFolderName.trim()) return
    try {
      await folders.create(newFolderName.trim()); setNewFolderName(''); loadFolders()
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to create folder') }
  }

  async function handleDeleteFolder(id: number) {
    try {
      await folders.delete(id)
      if (selectedFolder === id) setSelectedFolder(undefined)
      loadFolders(); loadDocs()
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to delete folder') }
  }

  async function handleMoveToFolder(docId: number, folderId: number | null) {
    try { await documents.moveToFolder(docId, folderId); setMoveDocId(null); loadDocs() }
    catch (err) { setError(err instanceof Error ? err.message : 'Failed to move document') }
  }

  async function openShareDialog(docId: number) {
    setShareDocId(docId); setShareSearch(''); setShareResults([]); setSelectedUser(null); setShareType('READ')
    try { setPermissions(await documents.permissions.list(docId)) } catch { setPermissions([]) }
  }

  async function handleGrantShare() {
    if (!shareDocId || !selectedUser) return
    try {
      await documents.permissions.grant(shareDocId, selectedUser.id, shareType)
      setSelectedUser(null); setShareSearch(''); setShareResults([])
      setPermissions(await documents.permissions.list(shareDocId))
      loadNotifications()
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to share') }
  }

  async function handleRevokeShare(userId: number) {
    if (!shareDocId) return
    try {
      await documents.permissions.revoke(shareDocId, userId)
      setPermissions(await documents.permissions.list(shareDocId))
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to revoke') }
  }

  async function handleToggleFavorite(doc: DocumentResponse) {
    try {
      const { favorite } = await documents.toggleFavorite(doc.id)
      if (activeTab === 'favorites' && !favorite) {
        setDocs(prev => prev.filter(d => d.id !== doc.id))
      } else {
        setDocs(prev => prev.map(d => d.id === doc.id ? { ...d, favorite } : d))
      }
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed') }
  }

  async function handleMarkNotifRead(id: number) {
    try { await notifApi.markRead(id); loadNotifications() } catch { /* ignore */ }
  }

  async function handleMarkAllRead() {
    try { await notifApi.markAllRead(); loadNotifications() } catch { /* ignore */ }
  }

  async function handleDeleteNotif(id: number) {
    try { await notifApi.delete(id); loadNotifications() } catch { /* ignore */ }
  }

  async function handleClearAllNotifs() {
    try { await notifApi.clearAll(); loadNotifications() } catch { /* ignore */ }
  }

  async function handleSearchUser(q: string) {
    setShareSearch(q)
    if (q.trim().length < 1) { setShareResults([]); return }
    try { setShareResults(await users.search(q)) } catch { setShareResults([]) }
  }

  // ---- Versions ----
  async function openVersions(docId: number) {
    setVersionDocId(docId)
    try { setVersions(await documents.getVersions(docId)) } catch { setVersions([]) }
  }

  // Editor
  async function openEditor(doc: DocumentResponse) {
    setEditDoc(doc)
    setEditLoading(true)
    try {
      const content = await documents.getContent(doc.id)
      setEditContent(content)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load content')
      setEditDoc(null)
    }
    setEditLoading(false)
  }

  async function handleSaveContent() {
    if (!editDoc) return
    try {
      const updated = await documents.updateContent(editDoc.id, editContent)
      setEditDoc(null)
      setEditContent('')
      // Update doc in list
      setDocs(prev => prev.map(d => d.id === updated.id ? { ...d, currentVersion: updated.currentVersion, versionCount: updated.versionCount } : d))
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to save content') }
  }

  async function handleRestoreVersion(versionId: number) {
    if (!versionDocId) return
    try {
      await documents.restoreVersion(versionDocId, versionId)
      setVersionDocId(null); loadDocs()
    } catch (err) { setError(err instanceof Error ? err.message : 'Restore failed') }
  }

  // ---- Batch ----
  function toggleSelect(id: number) {
    setSelectedIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  }

  function toggleSelectAll() {
    if (selectedIds.size === docs.length) { setSelectedIds(new Set()) }
    else { setSelectedIds(new Set(docs.map(d => d.id))) }
  }

  async function handleBatchDelete() {
    if (selectedIds.size === 0) return
    try {
      await documents.batchDelete([...selectedIds])
      setSelectedIds(new Set()); loadDocs()
    } catch (err) { setError(err instanceof Error ? err.message : 'Batch delete failed') }
  }

  async function handleBatchDownload() {
    if (selectedIds.size === 0) return
    try { await documents.batchDownload([...selectedIds]) }
    catch (err) { setError(err instanceof Error ? err.message : 'Batch download failed') }
  }

  async function handleBatchMove() {
    if (selectedIds.size === 0) return
    try {
      await documents.batchMove([...selectedIds], batchMoveFolderId)
      setSelectedIds(new Set()); loadDocs(); setBatchMoveFolderId(null)
    } catch (err) { setError(err instanceof Error ? err.message : 'Batch move failed') }
  }

  // ---- Helpers ----
  function tabClasses(tab: DocTab) {
    return `px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
      activeTab === tab
        ? 'bg-background border border-b-0 border-border text-foreground'
        : 'text-muted-foreground hover:text-foreground cursor-pointer'
    }`
  }

  const isOwner = (doc: DocumentResponse) => doc.ownerUsername === user?.username

  const typeColors: Record<string, string> = {
    PDF: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    Word: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    Excel: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    PowerPoint: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    Image: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    Text: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
    CSV: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    Other: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  }

  const isTrash = activeTab === 'trash'

  return (
    <div className="min-h-screen bg-muted/30 dark:bg-muted/10">
      <header className="border-b bg-background">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            <span className="font-semibold">Secure DMS</span>
          </div>
          <div className="flex items-center gap-3">
            {/* Notifications */}
            <div className="relative">
              <Button variant="ghost" size="icon" className="relative" onClick={() => { setShowNotif(!showNotif); if (!showNotif) loadNotifications() }}>
                <Bell className="h-5 w-5" />
                {notifCount > 0 && (
                  <span className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-destructive text-destructive-foreground text-[10px] font-bold flex items-center justify-center">
                    {notifCount > 99 ? '99+' : notifCount}
                  </span>
                )}
              </Button>
              {showNotif && (
                <div className="absolute right-0 top-full mt-2 bg-popover border rounded-xl shadow-lg z-50 w-80 max-h-96 overflow-y-auto" onClick={e => e.stopPropagation()}>
                  <div className="flex items-center justify-between px-4 py-2 border-b sticky top-0 bg-popover">
                    <p className="text-sm font-medium">Notifications</p>
                    <div className="flex items-center gap-2">
                      {notifList.length > 0 && (
                        <button className="text-xs text-muted-foreground hover:text-destructive" onClick={handleClearAllNotifs}>Clear all</button>
                      )}
                      {notifCount > 0 && (
                        <button className="text-xs text-primary hover:underline" onClick={handleMarkAllRead}>Mark all read</button>
                      )}
                    </div>
                  </div>
                  {notifList.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-6">No notifications</p>
                  ) : (
                    notifList.map(n => (
                      <div key={n.id} className={`px-4 py-3 border-b last:border-b-0 flex items-start justify-between gap-2 ${!n.read ? 'bg-muted/20' : ''}`}>
                        <div className="min-w-0 flex-1 cursor-pointer" onClick={() => { if (!n.read) handleMarkNotifRead(n.id) }}>
                          <p className="text-sm font-medium">{n.title}</p>
                          {n.message && <p className="text-xs text-muted-foreground">{n.message}</p>}
                        </div>
                        <Button variant="ghost" size="icon" className="h-6 w-6 shrink-0" onClick={() => handleDeleteNotif(n.id)} title="Delete">
                          <X className="h-3 w-3" />
                        </Button>
                      </div>
                    ))
                  )}
                </div>
              )}
            </div>

            {/* Dark mode toggle */}
            <Button variant="ghost" size="icon" onClick={toggleDark} title={dark ? 'Light mode' : 'Dark mode'}>
              {dark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
            </Button>

            {/* Profile */}
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

        {/* Upload */}
        {!isTrash && (
          <Card>
            <CardHeader>
              <CardTitle>Upload Document</CardTitle>
              <CardDescription>
                {activeTab !== 'mine'
                  ? 'Switch to My Documents to upload'
                  : selectedFolder
                    ? `Uploading to: ${folderList.find(f => f.id === selectedFolder)?.name || 'folder'}`
                    : 'Upload to root'}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleUpload} className="space-y-3">
                <div
                  className={`border-2 border-dashed rounded-lg p-6 text-center transition-colors ${
                    dragOver ? 'border-primary bg-primary/5' : 'border-border'
                  } ${activeTab !== 'mine' ? 'opacity-40' : 'cursor-pointer'}`}
                  onDragOver={onDragOver} onDragLeave={onDragLeave} onDrop={onDrop}
                  onClick={() => { if (activeTab === 'mine') document.getElementById('file-input')?.click() }}
                >
                  {file ? (
                    <div className="flex items-center justify-center gap-2">
                      <FileInput className="h-5 w-5 text-primary" />
                      <span className="text-sm font-medium">{file.name}</span>
                      {uploadMode === 'drop' && <span className="text-xs text-muted-foreground">(dropped)</span>}
                      <Button type="button" variant="ghost" size="icon" className="h-6 w-6" onClick={(e) => { e.stopPropagation(); setFile(null); setUploadMode('normal') }}>
                        <X className="h-3 w-3" />
                      </Button>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-1">
                      <Upload className="h-6 w-6 text-muted-foreground" />
                      <p className="text-sm text-muted-foreground">Drop file here or click to browse</p>
                    </div>
                  )}
                </div>
                <input id="file-input" type="file" className="hidden" onChange={(e) => { setFile(e.target.files?.[0] || null); setUploadMode('normal') }} disabled={activeTab !== 'mine'} />
                <div className="flex flex-wrap gap-3 items-end">
                  <div className="flex-1 min-w-[200px]">
                    <Input placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} disabled={activeTab !== 'mine'} />
                  </div>
                  <select
                    className="h-9 rounded-md border border-input bg-background px-3 text-sm disabled:opacity-40"
                    value={documentType} onChange={e => setDocumentType(e.target.value)} disabled={activeTab !== 'mine'}
                  >
                    <option value="">Auto-detect</option>
                    {DOC_TYPES.filter(Boolean).map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                  <Button type="submit" disabled={!file || activeTab !== 'mine'}>
                    <Upload className="h-4 w-4 mr-2" /> Upload
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        )}

        {/* Folders */}
        {activeTab === 'mine' && !isTrash && (
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div><CardTitle>Folders</CardTitle><CardDescription>Organize your documents</CardDescription></div>
              <Button variant="outline" size="sm" onClick={() => setShowFolders(!showFolders)}>
                <FolderPlus className="h-4 w-4 mr-2" /> Manage
              </Button>
            </CardHeader>
            <CardContent>
              {showFolders && (
                <div className="mb-4 flex gap-2">
                  <Input placeholder="New folder name" value={newFolderName} onChange={(e) => setNewFolderName(e.target.value)} />
                  <Button size="sm" onClick={handleCreateFolder}><FolderPlus className="h-4 w-4 mr-2" /> Create</Button>
                </div>
              )}
              <div className="flex flex-wrap gap-2">
                <Button variant={selectedFolder === undefined ? 'default' : 'outline'} size="sm"
                  onClick={() => { setSelectedFolder(undefined); loadDocs() }}>
                  <Folder className="h-4 w-4 mr-2" /> All
                </Button>
                {folderList.map(f => (
                  <div key={f.id} className="flex items-center gap-1">
                    <Button variant={selectedFolder === f.id ? 'default' : 'outline'} size="sm"
                      onClick={() => { setSelectedFolder(f.id); loadDocs() }}>
                      <Folder className="h-4 w-4 mr-2" /> {f.name} ({f.documentCount})
                    </Button>
                    {showFolders && (
                      <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => handleDeleteFolder(f.id)}>
                        <X className="h-3 w-3" />
                      </Button>
                    )}
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}

        {/* Recently viewed */}
        {activeTab === 'mine' && !isTrash && recentDocs.length > 0 && (
          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><Clock className="h-4 w-4" /> Recently Viewed</CardTitle></CardHeader>
            <CardContent>
              <div className="flex gap-3 overflow-x-auto pb-2">
                {recentDocs.slice(0, 8).map(doc => (
                  <div key={doc.id} className="flex items-center gap-2 px-3 py-2 rounded-lg border bg-card shrink-0 cursor-pointer hover:bg-muted/50 transition-colors" onClick={() => handleDownload(doc.id)}>
                    <FileText className="h-4 w-4 text-primary shrink-0" />
                    <span className="text-xs font-medium truncate max-w-[120px]">{doc.originalFilename}</span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}

        {/* Documents */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-1">
              {(['mine', 'shared-with-me', 'shared-by-me', 'favorites', 'trash'] as DocTab[]).map(tab => (
                <button key={tab} className={tabClasses(tab)} onClick={() => setActiveTab(tab)}>
                  {tab === 'trash' && <Archive className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'favorites' && <Star className="h-3.5 w-3.5 inline mr-1" />}
                  {tab === 'mine' ? 'My Documents' : tab === 'shared-with-me' ? 'Shared with me' : tab === 'shared-by-me' ? 'Shared by me' : tab === 'favorites' ? 'Favorites' : 'Trash'}
                </button>
              ))}
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Search + type filter */}
            <form onSubmit={(e) => { e.preventDefault(); loadDocs() }} className="flex gap-2">
              <div className="flex-1">
                <Input placeholder={isTrash ? 'Search trash...' : 'Search documents and content...'} value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} />
              </div>
              <Button type="submit" variant="secondary"><Search className="h-4 w-4 mr-2" /> Search</Button>
              {activeTab === 'mine' && !isTrash && (
                <div className="flex items-center gap-1 border-l border-border pl-2">
                  <Button variant={listView ? 'secondary' : 'ghost'} size="icon" onClick={() => setListView(true)} title="List view">
                    <List className="h-4 w-4" />
                  </Button>
                  <Button variant={!listView ? 'secondary' : 'ghost'} size="icon" onClick={() => setListView(false)} title="Grid view">
                    <Grid3X3 className="h-4 w-4" />
                  </Button>
                </div>
              )}
              {activeTab === 'mine' && !isTrash && (
                <select className="h-9 rounded-md border border-input bg-background px-2 text-xs"
                  value={`${sortBy},${sortDir}`}
                  onChange={e => { const [by, dir] = e.target.value.split(','); setSortBy(by); setSortDir(dir as 'asc' | 'desc') }}>
                  <option value="uploadedAt,desc">Newest</option>
                  <option value="uploadedAt,asc">Oldest</option>
                  <option value="originalFilename,asc">Name A-Z</option>
                  <option value="originalFilename,desc">Name Z-A</option>
                  <option value="fileSize,desc">Largest</option>
                  <option value="fileSize,asc">Smallest</option>
                </select>
              )}
            </form>

            {/* Type filter */}
            {activeTab === 'mine' && (
              <div className="flex flex-wrap gap-1">
                <button className={`px-2 py-1 text-xs rounded-full transition-colors ${!selectedDocType ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground hover:bg-muted/80'}`}
                  onClick={() => { setSelectedDocType(''); loadDocs() }}>All</button>
                {DOC_TYPES.filter(Boolean).map(t => (
                  <button key={t} className={`px-2 py-1 text-xs rounded-full transition-colors ${selectedDocType === t ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground hover:bg-muted/80'}`}
                    onClick={() => { setSelectedDocType(t); loadDocs() }}>{t}</button>
                ))}
              </div>
            )}

            {/* Batch toolbar */}
            {selectedIds.size > 0 && !isTrash && (
              <div className="flex items-center gap-2 px-3 py-2 bg-muted rounded-lg">
                <span className="text-sm font-medium">{selectedIds.size} selected</span>
                <div className="flex-1" />
                <Button size="sm" variant="outline" onClick={handleBatchDownload}>
                  <DownloadCloud className="h-4 w-4 mr-1" /> Download ZIP
                </Button>
                <div className="flex items-center gap-1">
                  <select className="h-8 rounded-md border border-input bg-background px-2 text-xs"
                    value={batchMoveFolderId ?? ''} onChange={e => setBatchMoveFolderId(e.target.value ? Number(e.target.value) : null)}>
                    <option value="">Move to...</option>
                    {folderList.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
                  </select>
                  <Button size="sm" variant="outline" onClick={handleBatchMove} disabled={batchMoveFolderId === null}>
                    <MoveRight className="h-4 w-4" />
                  </Button>
                </div>
                <Button size="sm" variant="destructive" onClick={handleBatchDelete}>
                  <Trash2 className="h-4 w-4 mr-1" /> Delete
                </Button>
              </div>
            )}

            {docs.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-8">
                {isTrash ? 'Trash is empty' : activeTab === 'favorites' ? 'No favorites yet' : 'No documents found'}
              </p>
            ) : listView ? (
              <div className="divide-y">
                {docs.map((doc) => (
                  <div key={doc.id} className="py-3 flex items-center justify-between gap-4">
                    <div className="min-w-0 flex-1 flex items-center gap-3">
                      {!isTrash && (
                        <button onClick={() => toggleSelect(doc.id)} className="shrink-0 text-muted-foreground hover:text-foreground">
                          {selectedIds.has(doc.id) ? <CheckSquare className="h-4 w-4" /> : <Square className="h-4 w-4" />}
                        </button>
                      )}
                      <div>
                        <p className="text-sm font-medium truncate flex items-center gap-2">
                          {doc.originalFilename}
                          {doc.documentType && (
                            <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium ${typeColors[doc.documentType] || typeColors['Other']}`}>
                              {doc.documentType}
                            </span>
                          )}
                          {(doc.versionCount ?? 0) > 1 && (
                            <span className="text-[10px] text-muted-foreground">v{doc.currentVersion}</span>
                          )}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {(doc.fileSize / 1024).toFixed(1)} KB
                          {doc.folderName && <span> &middot; {doc.folderName}</span>}
                          {!isOwner(doc) && <span> &middot; by {doc.ownerUsername}</span>}
                          {doc.deletedAt && <span> &middot; deleted</span>}
                          {(doc.versionCount ?? 0) > 1 && <span> &middot; {doc.versionCount} versions</span>}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                        doc.permission === 'OWNER' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
                          : doc.permission === 'WRITE' ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                          : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'
                      }`}>
                        {doc.permission === 'OWNER' ? 'Owner' : doc.permission === 'WRITE' ? 'Write' : doc.permission === 'READ' ? 'Read' : ''}
                      </span>

                      {!isTrash && (
                        <Button variant="ghost" size="icon" onClick={() => handleToggleFavorite(doc)}
                          title={doc.favorite ? 'Remove from favorites' : 'Add to favorites'}>
                          <Star className={`h-4 w-4 ${doc.favorite ? 'fill-yellow-400 text-yellow-400' : ''}`} />
                        </Button>
                      )}
                      {!isTrash && (
                        <Button variant="ghost" size="icon" onClick={async () => {
                          setPreviewDoc(doc); setPreviewBlobUrl(null)
                          try {
                            const url = await documents.getPreviewBlobUrl(doc.id, doc.contentType)
                            setPreviewBlobUrl(url)
                          } catch { setError('Failed to load preview') }
                        }} title="Preview">
                          <Eye className="h-4 w-4" />
                        </Button>
                      )}
                      {!isTrash && (isOwner(doc) || doc.permission === 'WRITE') && doc.contentType?.startsWith('text/') && (
                        <Button variant="ghost" size="icon" onClick={() => openEditor(doc)} title="Edit">
                          <Pencil className="h-4 w-4" />
                        </Button>
                      )}

                      {isTrash ? (
                        <Button variant="ghost" size="icon" onClick={() => handleRestore(doc.id)} title="Restore">
                          <RotateCcw className="h-4 w-4" />
                        </Button>
                      ) : (
                        <>
                          <Button variant="ghost" size="icon" onClick={() => handleDownload(doc.id)} title="Download">
                            <Download className="h-4 w-4" />
                          </Button>
                        </>
                      )}

                      {isOwner(doc) && !isTrash && (
                        <>
                          <Button variant="ghost" size="icon" onClick={() => openVersions(doc.id)} title="Version history">
                            <History className="h-4 w-4" />
                          </Button>
                          <div className="relative">
                            <Button variant="ghost" size="icon" onClick={() => setMoveDocId(moveDocId === doc.id ? null : doc.id)} title="Move to folder">
                              <MoveRight className="h-4 w-4" />
                            </Button>
                            {moveDocId === doc.id && (
                              <div className="absolute right-0 top-full mt-1 bg-popover border rounded-lg shadow-lg p-2 z-50 min-w-[180px]">
                                <p className="text-xs font-medium text-muted-foreground px-2 py-1">Move to folder</p>
                                <button className="w-full text-left px-2 py-1.5 text-sm rounded hover:bg-muted transition-colors"
                                  onClick={() => handleMoveToFolder(doc.id, null)}>(No folder)</button>
                                {folderList.map(f => (
                                  <button key={f.id} className="w-full text-left px-2 py-1.5 text-sm rounded hover:bg-muted transition-colors"
                                    onClick={() => handleMoveToFolder(doc.id, f.id)}>{f.name}</button>
                                ))}
                              </div>
                            )}
                          </div>
                          {!isTrash && <Button variant="ghost" size="icon" onClick={() => openShareDialog(doc.id)} title="Share">
                            <Share2 className="h-4 w-4" />
                          </Button>}
                        </>
                      )}

                      {!isTrash && (
                        <Button variant="ghost" size="icon" onClick={() => handleDelete(doc.id)}
                          title={isOwner(doc) ? 'Move to trash' : 'Remove access'}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                      {isTrash && (
                        <Button variant="ghost" size="icon" onClick={() => handleDelete(doc.id)} title="Delete permanently">
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                {docs.map((doc) => (
                  <div key={doc.id} className="border rounded-lg p-3 hover:bg-muted/50 transition-colors">
                    <div className="flex items-start justify-between mb-2">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium truncate flex items-center gap-2">
                          {doc.originalFilename}
                          {doc.documentType && (
                            <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium ${typeColors[doc.documentType] || typeColors['Other']}`}>
                              {doc.documentType}
                            </span>
                          )}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {(doc.fileSize / 1024).toFixed(1)} KB
                          {!isOwner(doc) && <span> &middot; {doc.ownerUsername}</span>}
                        </p>
                      </div>
                      {!isTrash && (
                        <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => handleToggleFavorite(doc)}
                          title={doc.favorite ? 'Remove from favorites' : 'Add to favorites'}>
                          <Star className={`h-3.5 w-3.5 ${doc.favorite ? 'fill-yellow-400 text-yellow-400' : ''}`} />
                        </Button>
                      )}
                    </div>
                    <div className="flex items-center gap-1">
                      {!isTrash && (
                        <Button variant="ghost" size="icon" className="h-7 w-7" title="Preview" onClick={async () => {
                          setPreviewDoc(doc); setPreviewBlobUrl(null)
                          try { setPreviewBlobUrl(await documents.getPreviewBlobUrl(doc.id, doc.contentType)) }
                          catch { setError('Failed to load preview') }
                        }}>
                          <Eye className="h-3.5 w-3.5" />
                        </Button>
                      )}
                      <Button variant="ghost" size="icon" className="h-7 w-7" title="Download" onClick={() => handleDownload(doc.id)}>
                        <Download className="h-3.5 w-3.5" />
                      </Button>
                      {isOwner(doc) && !isTrash && (
                        <Button variant="ghost" size="icon" className="h-7 w-7" title="Share" onClick={() => openShareDialog(doc.id)}>
                          <Share2 className="h-3.5 w-3.5" />
                        </Button>
                      )}
                      {isTrash ? (
                        <Button variant="ghost" size="icon" className="h-7 w-7" title="Restore" onClick={() => handleRestore(doc.id)}>
                          <RotateCcw className="h-3.5 w-3.5" />
                        </Button>
                      ) : (
                        <Button variant="ghost" size="icon" className="h-7 w-7" title={isOwner(doc) ? 'Move to trash' : 'Remove access'} onClick={() => handleDelete(doc.id)}>
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Select all */}
            {!isTrash && docs.length > 0 && (
              <button className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
                onClick={toggleSelectAll}>
                {selectedIds.size === docs.length ? <CheckSquare className="h-3 w-3" /> : <Square className="h-3 w-3" />}
                {selectedIds.size === docs.length ? 'Deselect all' : 'Select all'}
              </button>
            )}
          </CardContent>
        </Card>
      </main>

      {/* Share Dialog */}
      {shareDocId && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={() => setShareDocId(null)}>
          <div className="bg-background rounded-xl p-6 w-full max-w-md shadow-lg" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-lg">Share Document</h3>
              <Button variant="ghost" size="icon" onClick={() => setShareDocId(null)}><X className="h-4 w-4" /></Button>
            </div>
            <div className="mb-4">
              <div className="flex gap-2">
                <div className="flex-1 relative">
                  <Input placeholder="Search users..." value={shareSearch} onChange={e => handleSearchUser(e.target.value)} />
                  {shareResults.length > 0 && (
                    <div className="absolute top-full left-0 right-0 mt-1 bg-popover border rounded-lg shadow-lg z-50 max-h-48 overflow-y-auto">
                      {shareResults.map(u => (
                        <button key={u.id}
                          className={`w-full text-left px-3 py-2 text-sm hover:bg-muted transition-colors ${selectedUser?.id === u.id ? 'bg-muted font-medium' : ''}`}
                          onClick={() => { setSelectedUser(u); setShareSearch(u.username); setShareResults([]) }}>
                          {u.username}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <select className="h-9 rounded-md border border-input bg-background px-3 text-sm" value={shareType}
                  onChange={e => setShareType(e.target.value as 'READ' | 'WRITE')}>
                  <option value="READ">Read</option>
                  <option value="WRITE">Write</option>
                </select>
                <Button size="sm" onClick={handleGrantShare} disabled={!selectedUser}>
                  <UserPlus className="h-4 w-4 mr-2" /> Share
                </Button>
              </div>
            </div>
            {permissions.length > 0 && (
              <div className="divide-y">
                <p className="text-xs text-muted-foreground mb-2">Shared with:</p>
                {permissions.map(p => (
                  <div key={p.id} className="py-2 flex items-center justify-between">
                    <span className="text-sm">{p.username} ({p.permissionType})</span>
                    <Button variant="ghost" size="sm" onClick={() => handleRevokeShare(p.userId)}>
                      <X className="h-3 w-3 mr-1" /> Revoke
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Preview Dialog */}
      {previewDoc && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={() => { if (previewBlobUrl) URL.revokeObjectURL(previewBlobUrl); setPreviewDoc(null) }}>
          <div className="bg-background rounded-xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-lg" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-4 border-b shrink-0">
              <div>
                <h3 className="font-semibold">{previewDoc.originalFilename}</h3>
                <p className="text-xs text-muted-foreground">{(previewDoc.fileSize / 1024).toFixed(1)} KB &middot; {previewDoc.contentType}</p>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" onClick={() => handleDownload(previewDoc.id)}>
                  <Download className="h-4 w-4 mr-2" /> Download
                </Button>
                <Button variant="ghost" size="icon" onClick={() => { if (previewBlobUrl) URL.revokeObjectURL(previewBlobUrl); setPreviewDoc(null) }}><X className="h-4 w-4" /></Button>
              </div>
            </div>
            <div className="flex-1 overflow-auto p-6 bg-muted/20 flex items-start justify-center">
              {!previewBlobUrl && !previewDoc.contentType.startsWith('image/') && previewDoc.contentType !== 'application/pdf' && !previewDoc.contentType.startsWith('text/') ? (
                <div className="text-center py-12">
                  <FileText className="h-12 w-12 text-muted-foreground mx-auto mb-3" />
                  <p className="text-sm text-muted-foreground">
                    {previewDoc.contentType.includes('officedocument') || previewDoc.contentType.includes('opendocument') || previewDoc.contentType === 'application/msword' || previewDoc.contentType === 'application/vnd.ms-excel' || previewDoc.contentType === 'application/vnd.ms-powerpoint' || previewDoc.contentType === 'application/rtf'
                      ? 'Office documents cannot be previewed in the browser'
                      : 'Preview not available for this file type'}
                  </p>
                  <Button variant="outline" size="sm" className="mt-3" onClick={() => handleDownload(previewDoc.id)}>
                    <Download className="h-4 w-4 mr-2" /> Download to view
                  </Button>
                </div>
              ) : !previewBlobUrl ? (
                <div className="text-center py-12">
                  <FileText className="h-12 w-12 text-muted-foreground mx-auto mb-3" />
                  <p className="text-sm text-muted-foreground">Loading preview...</p>
                </div>
              ) : previewDoc.contentType.startsWith('image/') ? (
                <img src={previewBlobUrl} alt={previewDoc.originalFilename}
                  className="max-w-full max-h-[70vh] rounded-lg shadow" />
              ) : previewDoc.contentType === 'application/pdf' ? (
                <embed src={previewBlobUrl} type="application/pdf" className="w-full h-[70vh] rounded-lg" />
              ) : previewDoc.contentType.startsWith('text/') ? (
                <iframe src={previewBlobUrl} className="w-full h-[70vh] rounded-lg bg-white" title="Preview" />
              ) : (
                <div className="text-center py-12">
                  <FileText className="h-12 w-12 text-muted-foreground mx-auto mb-3" />
                  <p className="text-sm text-muted-foreground">Preview not available for this file type</p>
                  <Button variant="outline" size="sm" className="mt-3" onClick={() => handleDownload(previewDoc.id)}>
                    <Download className="h-4 w-4 mr-2" /> Download to view
                  </Button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Editor Dialog */}
      {editDoc && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={() => setEditDoc(null)}>
          <div className="bg-background rounded-xl w-full max-w-3xl max-h-[90vh] flex flex-col shadow-lg" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-4 border-b shrink-0">
              <div>
                <h3 className="font-semibold flex items-center gap-2">
                  <Pencil className="h-4 w-4" /> Edit: {editDoc.originalFilename}
                </h3>
                <p className="text-xs text-muted-foreground">
                  {editDoc.contentType} &middot; {(editDoc.fileSize / 1024).toFixed(1)} KB
                </p>
              </div>
              <Button variant="ghost" size="icon" onClick={() => setEditDoc(null)}><X className="h-4 w-4" /></Button>
            </div>
            <div className="flex-1 p-4">
              {editLoading ? (
                <div className="flex items-center justify-center h-64">
                  <p className="text-sm text-muted-foreground">Loading content...</p>
                </div>
              ) : (
                <textarea
                  className="w-full h-64 md:h-96 p-4 rounded-lg border border-input bg-background text-sm font-mono resize-none focus:outline-none focus:ring-2 focus:ring-ring"
                  value={editContent}
                  onChange={e => setEditContent(e.target.value)}
                />
              )}
            </div>
            <div className="flex items-center justify-end gap-2 px-6 py-4 border-t shrink-0">
              <p className="text-xs text-muted-foreground flex-1">
                Saving creates a new version (v{(editDoc.currentVersion ?? 0) + 1})
              </p>
              <Button variant="outline" onClick={() => setEditDoc(null)}>Cancel</Button>
              <Button onClick={handleSaveContent} disabled={editLoading}>
                <Pencil className="h-4 w-4 mr-2" /> Save
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Versions Dialog */}
      {versionDocId && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={() => setVersionDocId(null)}>
          <div className="bg-background rounded-xl p-6 w-full max-w-lg shadow-lg" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-lg flex items-center gap-2">
                <History className="h-4 w-4" /> Version History
              </h3>
              <Button variant="ghost" size="icon" onClick={() => setVersionDocId(null)}><X className="h-4 w-4" /></Button>
            </div>
            {versions.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-6">No versions found</p>
            ) : (
              <div className="divide-y max-h-80 overflow-y-auto">
                {versions.map(v => (
                  <div key={v.id} className="py-3 flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium">Version {v.versionNumber}</p>
                      <p className="text-xs text-muted-foreground">
                        {(v.fileSize / 1024).toFixed(1)} KB
                        {v.uploadedByUsername && <span> &middot; by {v.uploadedByUsername}</span>}
                        <span> &middot; {new Date(v.createdAt).toLocaleDateString()}</span>
                      </p>
                    </div>
                    <Button variant="outline" size="sm" onClick={() => handleRestoreVersion(v.id)}>
                      <RotateCcw className="h-3 w-3 mr-1" /> Restore
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
