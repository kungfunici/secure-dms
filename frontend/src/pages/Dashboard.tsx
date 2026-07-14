import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/contexts/AuthContext'
import { folders, documents, users, type DocumentResponse, type FolderResponse, type PermissionResponse, type UserResponse } from '@/lib/api'
import { FileText, LogOut, Search, Upload, Download, Trash2, FolderPlus, Folder, Share2, X, UserPlus, MoveRight } from 'lucide-react'

type Tab = 'mine' | 'shared-with-me' | 'shared-by-me'

export default function Dashboard() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [docs, setDocs] = useState<DocumentResponse[]>([])
  const [folderList, setFolderList] = useState<FolderResponse[]>([])
  const [activeTab, setActiveTab] = useState<Tab>('mine')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedFolder, setSelectedFolder] = useState<number | undefined>(undefined)
  const [file, setFile] = useState<File | null>(null)
  const [description, setDescription] = useState('')
  const [error, setError] = useState('')
  const [showFolders, setShowFolders] = useState(false)
  const [newFolderName, setNewFolderName] = useState('')

  // Sharing state
  const [shareDocId, setShareDocId] = useState<number | null>(null)
  const [shareSearch, setShareSearch] = useState('')
  const [shareResults, setShareResults] = useState<UserResponse[]>([])
  const [selectedUser, setSelectedUser] = useState<UserResponse | null>(null)
  const [shareType, setShareType] = useState<'READ' | 'WRITE'>('READ')
  const [permissions, setPermissions] = useState<PermissionResponse[]>([])

  // Move state
  const [moveDocId, setMoveDocId] = useState<number | null>(null)

  async function loadDocs() {
    try {
      if (searchQuery) {
        const res = await documents.search(searchQuery)
        setDocs(res.content)
        return
      }
      if (activeTab === 'mine') {
        const res = await documents.list()
        let all = res.content
        if (selectedFolder !== undefined) {
          all = all.filter(d => d.folderId === selectedFolder)
        }
        setDocs(all)
      } else if (activeTab === 'shared-with-me') {
        const res = await documents.sharedWithMe()
        setDocs(res.content)
      } else if (activeTab === 'shared-by-me') {
        const res = await documents.sharedByMe()
        setDocs(res.content)
      }
    } catch (err) {
      setError('Failed to load documents')
    }
  }

  async function loadFolders() {
    try {
      setFolderList(await folders.list())
    } catch { /* ignore */ }
  }

  useEffect(() => { loadDocs(); loadFolders() }, [activeTab, selectedFolder])

  async function handleSearchUser(q: string) {
    setShareSearch(q)
    if (q.trim().length < 1) {
      setShareResults([])
      return
    }
    try {
      setShareResults(await users.search(q))
    } catch { setShareResults([]) }
  }

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    loadDocs()
  }

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault()
    if (!file) return
    setError('')
    try {
      await documents.upload(file, description, selectedFolder)
      setFile(null)
      setDescription('')
      loadDocs()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    }
  }

  async function handleDownload(id: number) {
    try {
      await documents.download(id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Download failed')
    }
  }

  async function handleDelete(id: number) {
    try {
      await documents.delete(id)
      loadDocs()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed')
    }
  }

  async function handleCreateFolder() {
    if (!newFolderName.trim()) return
    try {
      await folders.create(newFolderName.trim())
      setNewFolderName('')
      loadFolders()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create folder')
    }
  }

  async function handleDeleteFolder(id: number) {
    try {
      await folders.delete(id)
      if (selectedFolder === id) setSelectedFolder(undefined)
      loadFolders()
      loadDocs()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete folder')
    }
  }

  async function handleMoveToFolder(docId: number, folderId: number | null) {
    try {
      await documents.moveToFolder(docId, folderId)
      setMoveDocId(null)
      loadDocs()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to move document')
    }
  }

  async function openShareDialog(docId: number) {
    setShareDocId(docId)
    setShareSearch('')
    setShareResults([])
    setSelectedUser(null)
    setShareType('READ')
    try {
      setPermissions(await documents.permissions.list(docId))
    } catch { setPermissions([]) }
  }

  async function handleGrantShare() {
    if (!shareDocId || !selectedUser) return
    try {
      await documents.permissions.grant(shareDocId, selectedUser.id, shareType)
      setSelectedUser(null)
      setShareSearch('')
      setShareResults([])
      setPermissions(await documents.permissions.list(shareDocId))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to share')
    }
  }

  async function handleRevokeShare(userId: number) {
    if (!shareDocId) return
    try {
      await documents.permissions.revoke(shareDocId, userId)
      setPermissions(await documents.permissions.list(shareDocId))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to revoke')
    }
  }

  function tabClasses(tab: Tab) {
    return `px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
      activeTab === tab
        ? 'bg-background border border-b-0 border-border text-foreground'
        : 'text-muted-foreground hover:text-foreground cursor-pointer'
    }`
  }

  const isOwner = (doc: DocumentResponse) => doc.ownerUsername === user?.username

  return (
    <div className="min-h-screen bg-muted/30">
      <header className="border-b bg-background">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            <span className="font-semibold">Secure DMS</span>
          </div>
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate(`/profile/${user?.id}`)}
              className="flex items-center gap-2 hover:opacity-80 transition-opacity"
            >
              <img
                src={users.avatarUrl(user?.id ?? 0)}
                alt="Avatar"
                className="w-8 h-8 rounded-full object-cover border border-border"
                onError={(e) => { (e.target as HTMLImageElement).src = '/default-avatar.svg' }}
              />
              <span className="text-sm text-muted-foreground">{user?.username}</span>
            </button>
            <Button variant="ghost" size="sm" onClick={logout}>
              <LogOut className="h-4 w-4 mr-2" /> Logout
            </Button>
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
            <form onSubmit={handleUpload} className="flex flex-wrap gap-3 items-end">
              <div className="flex-1 min-w-[200px]">
                <Input type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} disabled={activeTab !== 'mine'} />
              </div>
              <div className="flex-1 min-w-[200px]">
                <Input placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} disabled={activeTab !== 'mine'} />
              </div>
              <Button type="submit" disabled={!file || activeTab !== 'mine'}>
                <Upload className="h-4 w-4 mr-2" /> Upload
              </Button>
            </form>
          </CardContent>
        </Card>

        {/* Folders */}
        {activeTab === 'mine' && (
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Folders</CardTitle>
                <CardDescription>Organize your documents</CardDescription>
              </div>
              <Button variant="outline" size="sm" onClick={() => setShowFolders(!showFolders)}>
                <FolderPlus className="h-4 w-4 mr-2" /> Manage
              </Button>
            </CardHeader>
            <CardContent>
              {showFolders && (
                <div className="mb-4 flex gap-2">
                  <Input placeholder="New folder name" value={newFolderName} onChange={(e) => setNewFolderName(e.target.value)} />
                  <Button size="sm" onClick={handleCreateFolder}>
                    <FolderPlus className="h-4 w-4 mr-2" /> Create
                  </Button>
                </div>
              )}
              <div className="flex flex-wrap gap-2">
                <Button
                  variant={selectedFolder === undefined ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => { setSelectedFolder(undefined); loadDocs() }}
                >
                  <Folder className="h-4 w-4 mr-2" /> All
                </Button>
                {folderList.map(f => (
                  <div key={f.id} className="flex items-center gap-1">
                    <Button
                      variant={selectedFolder === f.id ? 'default' : 'outline'}
                      size="sm"
                      onClick={() => { setSelectedFolder(f.id); loadDocs() }}
                    >
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

        {/* Documents */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-1">
              <button className={tabClasses('mine')} onClick={() => setActiveTab('mine')}>My Documents</button>
              <button className={tabClasses('shared-with-me')} onClick={() => setActiveTab('shared-with-me')}>Shared with me</button>
              <button className={tabClasses('shared-by-me')} onClick={() => setActiveTab('shared-by-me')}>Shared by me</button>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <form onSubmit={handleSearch} className="flex gap-2">
              <div className="flex-1">
                <Input
                  placeholder="Search documents..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
              <Button type="submit" variant="secondary">
                <Search className="h-4 w-4 mr-2" /> Search
              </Button>
            </form>

            {docs.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-8">No documents found</p>
            ) : (
              <div className="divide-y">
                {docs.map((doc) => (
                  <div key={doc.id} className="py-3 flex items-center justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium truncate">{doc.originalFilename}</p>
                      <p className="text-xs text-muted-foreground">
                        {(doc.fileSize / 1024).toFixed(1)} KB
                        {doc.folderName && <span> &middot; {doc.folderName}</span>}
                        {!isOwner(doc) && <span> &middot; by {doc.ownerUsername}</span>}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                        doc.permission === 'OWNER'
                          ? 'bg-blue-100 text-blue-700'
                          : doc.permission === 'WRITE'
                            ? 'bg-green-100 text-green-700'
                            : 'bg-gray-100 text-gray-700'
                      }`}>
                        {doc.permission === 'OWNER' ? 'Owner' : doc.permission === 'WRITE' ? 'Write' : doc.permission === 'READ' ? 'Read' : ''}
                      </span>
                      <Button variant="ghost" size="icon" onClick={() => handleDownload(doc.id)} title="Download">
                        <Download className="h-4 w-4" />
                      </Button>
                      {isOwner(doc) && (
                        <>
                          <div className="relative">
                            <Button variant="ghost" size="icon" onClick={() => setMoveDocId(moveDocId === doc.id ? null : doc.id)} title="Move to folder">
                              <MoveRight className="h-4 w-4" />
                            </Button>
                            {moveDocId === doc.id && (
                              <div className="absolute right-0 top-full mt-1 bg-popover border rounded-lg shadow-lg p-2 z-50 min-w-[180px]">
                                <p className="text-xs font-medium text-muted-foreground px-2 py-1">Move to folder</p>
                                <button
                                  className="w-full text-left px-2 py-1.5 text-sm rounded hover:bg-muted transition-colors"
                                  onClick={() => handleMoveToFolder(doc.id, null)}
                                >
                                  (No folder)
                                </button>
                                {folderList.map(f => (
                                  <button
                                    key={f.id}
                                    className="w-full text-left px-2 py-1.5 text-sm rounded hover:bg-muted transition-colors"
                                    onClick={() => handleMoveToFolder(doc.id, f.id)}
                                  >
                                    {f.name}
                                  </button>
                                ))}
                              </div>
                            )}
                          </div>
                          <Button variant="ghost" size="icon" onClick={() => openShareDialog(doc.id)} title="Share">
                            <Share2 className="h-4 w-4" />
                          </Button>
                        </>
                      )}
                      <Button variant="ghost" size="icon" onClick={() => handleDelete(doc.id)} title={isOwner(doc) ? 'Delete' : 'Remove access'}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
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
              <Button variant="ghost" size="icon" onClick={() => setShareDocId(null)}>
                <X className="h-4 w-4" />
              </Button>
            </div>

            <div className="mb-4">
              <div className="flex gap-2">
                <div className="flex-1 relative">
                  <Input
                    placeholder="Search users..."
                    value={shareSearch}
                    onChange={e => handleSearchUser(e.target.value)}
                  />
                  {shareResults.length > 0 && (
                    <div className="absolute top-full left-0 right-0 mt-1 bg-popover border rounded-lg shadow-lg z-50 max-h-48 overflow-y-auto">
                      {shareResults.map(u => (
                        <button
                          key={u.id}
                          className={`w-full text-left px-3 py-2 text-sm hover:bg-muted transition-colors ${
                            selectedUser?.id === u.id ? 'bg-muted font-medium' : ''
                          }`}
                          onClick={() => {
                            setSelectedUser(u)
                            setShareSearch(u.username)
                            setShareResults([])
                          }}
                        >
                          {u.username}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <select
                  className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                  value={shareType}
                  onChange={e => setShareType(e.target.value as 'READ' | 'WRITE')}
                >
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
    </div>
  )
}
