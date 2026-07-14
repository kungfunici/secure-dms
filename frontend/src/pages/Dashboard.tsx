import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/contexts/AuthContext'
import { folders, documents, type DocumentResponse, type FolderResponse, type PermissionResponse } from '@/lib/api'
import { FileText, LogOut, Search, Upload, Download, Trash2, FolderPlus, Folder, Share2, X, UserPlus } from 'lucide-react'

export default function Dashboard() {
  const { user, logout } = useAuth()
  const [docs, setDocs] = useState<DocumentResponse[]>([])
  const [folderList, setFolderList] = useState<FolderResponse[]>([])
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedFolder, setSelectedFolder] = useState<number | undefined>(undefined)
  const [file, setFile] = useState<File | null>(null)
  const [description, setDescription] = useState('')
  const [error, setError] = useState('')
  const [showFolders, setShowFolders] = useState(false)
  const [newFolderName, setNewFolderName] = useState('')

  // Sharing state
  const [shareDocId, setShareDocId] = useState<number | null>(null)
  const [shareUsername, setShareUsername] = useState('')
  const [shareType, setShareType] = useState<'READ' | 'WRITE'>('READ')
  const [permissions, setPermissions] = useState<PermissionResponse[]>([])

  async function loadDocs(q?: string, folderId?: number) {
    try {
      if (q) {
        const res = await documents.search(q)
        setDocs(res.content)
      } else {
        const res = await documents.list()
        let all = res.content
        if (folderId !== undefined) {
          all = all.filter(d => d.folderId === folderId)
        }
        setDocs(all)
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

  useEffect(() => { loadDocs(); loadFolders() }, [])

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    loadDocs(searchQuery, selectedFolder)
  }

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault()
    if (!file) return
    setError('')
    try {
      await documents.upload(file, description, selectedFolder)
      setFile(null)
      setDescription('')
      loadDocs(searchQuery, selectedFolder)
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
      loadDocs(searchQuery, selectedFolder)
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
      loadDocs(searchQuery, undefined)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete folder')
    }
  }

  async function openShareDialog(docId: number) {
    setShareDocId(docId)
    setShareUsername('')
    setShareType('READ')
    try {
      setPermissions(await documents.permissions.list(docId))
    } catch { setPermissions([]) }
  }

  async function handleGrantShare() {
    if (!shareDocId || !shareUsername.trim()) return
    try {
      await documents.permissions.grant(shareDocId, shareUsername.trim(), shareType)
      setShareUsername('')
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
            <span className="text-sm text-muted-foreground">{user?.username}</span>
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
              {selectedFolder
                ? `Uploading to: ${folderList.find(f => f.id === selectedFolder)?.name || 'folder'}`
                : 'Upload to root'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleUpload} className="flex flex-wrap gap-3 items-end">
              <div className="flex-1 min-w-[200px]">
                <Input type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} />
              </div>
              <div className="flex-1 min-w-[200px]">
                <Input placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
              </div>
              <Button type="submit" disabled={!file}>
                <Upload className="h-4 w-4 mr-2" /> Upload
              </Button>
            </form>
          </CardContent>
        </Card>

        {/* Folders */}
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
                onClick={() => { setSelectedFolder(undefined); loadDocs(searchQuery, undefined) }}
              >
                <Folder className="h-4 w-4 mr-2" /> All
              </Button>
              {folderList.map(f => (
                <div key={f.id} className="flex items-center gap-1">
                  <Button
                    variant={selectedFolder === f.id ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => { setSelectedFolder(f.id); loadDocs(searchQuery, f.id) }}
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

        {/* Documents */}
        <Card>
          <CardHeader>
            <CardTitle>Documents</CardTitle>
            <CardDescription>Browse and manage your documents</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form onSubmit={handleSearch} className="flex gap-2">
              <div className="flex-1">
                <Input placeholder="Search documents..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} />
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
                  <div key={doc.id} className="py-3 flex items-center justify-between">
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium truncate">{doc.originalFilename}</p>
                      <p className="text-xs text-muted-foreground">
                        {doc.description} &middot; {(doc.fileSize / 1024).toFixed(1)} KB
                        {doc.folderName && <span> &middot; 📁 {doc.folderName}</span>}
                        {!isOwner(doc) && <span> &middot; shared by {doc.ownerUsername}</span>}
                      </p>
                    </div>
                    <div className="flex gap-1 ml-4">
                      <Button variant="ghost" size="icon" onClick={() => handleDownload(doc.id)} title="Download">
                        <Download className="h-4 w-4" />
                      </Button>
                      {isOwner(doc) && (
                        <Button variant="ghost" size="icon" onClick={() => openShareDialog(doc.id)} title="Share">
                          <Share2 className="h-4 w-4" />
                        </Button>
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

            <div className="flex gap-2 mb-4">
              <Input placeholder="Username" value={shareUsername} onChange={e => setShareUsername(e.target.value)} />
              <select
                className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                value={shareType}
                onChange={e => setShareType(e.target.value as 'READ' | 'WRITE')}
              >
                <option value="READ">Read</option>
                <option value="WRITE">Write</option>
              </select>
              <Button size="sm" onClick={handleGrantShare}>
                <UserPlus className="h-4 w-4 mr-2" /> Share
              </Button>
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
