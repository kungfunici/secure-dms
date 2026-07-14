import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/contexts/AuthContext'
import { auth, users, type UserResponse } from '@/lib/api'
import { ArrowLeft, Upload, Trash2, FileText, Clock, Lock } from 'lucide-react'

export default function ProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [profile, setProfile] = useState<UserResponse | null>(null)
  const [avatarFile, setAvatarFile] = useState<File | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(true)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  useEffect(() => {
    if (!user) return
    users.getProfile(user.id)
      .then(setProfile)
      .catch(() => setError('Failed to load profile'))
      .finally(() => setLoading(false))
  }, [user])

  async function handleAvatarUpload(e: React.FormEvent) {
    e.preventDefault()
    if (!avatarFile || !user) return
    setError('')
    setSuccess('')
    try {
      const updated = await users.updateProfile(user.id, avatarFile)
      setProfile(updated)
      setAvatarFile(null)
      setSuccess('Avatar updated')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update avatar')
    }
  }

  async function handleDeleteAccount() {
    if (!user) return
    if (!window.confirm('Are you sure you want to delete your account? This action cannot be undone.')) return
    try {
      await users.deleteAccount(user.id)
      logout()
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete account')
    }
  }

  if (loading || !profile) {
    return (
      <div className="min-h-screen bg-muted/30 flex items-center justify-center">
        <p className="text-muted-foreground">Loading...</p>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-muted/30">
      <header className="border-b bg-background">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            <span className="font-semibold">Secure DMS</span>
          </div>
          <Button variant="ghost" size="sm" onClick={() => navigate('/')}>
            <ArrowLeft className="h-4 w-4 mr-2" /> Back
          </Button>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-4 py-8 space-y-6">
        {error && (
          <div className="bg-destructive/10 text-destructive text-sm p-3 rounded-md">
            {error}
            <button className="ml-2 font-bold" onClick={() => setError('')}>×</button>
          </div>
        )}

        {success && (
          <div className="bg-green-50 text-green-700 text-sm p-3 rounded-md">
            {success}
          </div>
        )}

        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
            <CardDescription>{profile.username}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="flex items-center gap-4">
              <img
                src={profile.profilePicture ? users.avatarUrl(profile.id) : '/default-avatar.svg'}
                alt="Avatar"
                className="w-20 h-20 rounded-full object-cover border-2 border-border"
                onError={(e) => { (e.target as HTMLImageElement).src = '/default-avatar.svg' }}
              />
              <div>
                <p className="font-medium text-lg">{profile.username}</p>
                <p className="text-sm text-muted-foreground">{profile.email}</p>
              </div>
            </div>

            <form onSubmit={handleAvatarUpload} className="flex items-end gap-3">
              <div className="flex-1">
                <p className="text-sm font-medium mb-1">Change avatar</p>
                <Input
                  type="file"
                  accept="image/*"
                  onChange={(e) => setAvatarFile(e.target.files?.[0] || null)}
                />
              </div>
              <Button type="submit" disabled={!avatarFile}>
                <Upload className="h-4 w-4 mr-2" /> Upload
              </Button>
            </form>

            <hr className="border-border" />

            <div>
              <h3 className="font-medium mb-2 flex items-center gap-2"><Clock className="h-4 w-4" /> Version Retention</h3>
              <p className="text-sm text-muted-foreground mb-3">
                Old version snapshots are automatically deleted after this many days. Set to 0 to keep versions indefinitely.
              </p>
              <form onSubmit={async (e) => {
                e.preventDefault()
                setError('')
                setSuccess('')
                try {
                  const updated = await users.updateProfile(user!.id, null, profile.versionRetentionDays)
                  setProfile(updated)
                  setSuccess('Retention setting saved')
                } catch (err) {
                  setError(err instanceof Error ? err.message : 'Failed to save setting')
                }
              }} className="flex items-end gap-3">
                <div className="flex-1">
                  <p className="text-sm font-medium mb-1">Retention period (days)</p>
                  <Input
                    type="number"
                    min={0}
                    max={3650}
                    value={profile.versionRetentionDays}
                    onChange={(e) => setProfile({ ...profile, versionRetentionDays: parseInt(e.target.value) || 0 })}
                  />
                </div>
                <Button type="submit">
                  <Clock className="h-4 w-4 mr-2" /> Save
                </Button>
              </form>
            </div>

            <hr className="border-border" />

            <div>
              <h3 className="font-medium mb-2 flex items-center gap-2"><Lock className="h-4 w-4" /> Change Password</h3>
              <form onSubmit={async (e) => {
                e.preventDefault()
                setError('')
                setSuccess('')
                if (newPassword !== confirmPassword) { setError('Passwords do not match'); return }
                try {
                  await auth.changePassword(currentPassword, newPassword)
                  setCurrentPassword(''); setNewPassword(''); setConfirmPassword('')
                  setSuccess('Password changed')
                } catch (err) {
                  setError(err instanceof Error ? err.message : 'Failed to change password')
                }
              }} className="space-y-3">
                <div>
                  <p className="text-sm font-medium mb-1">Current password</p>
                  <Input type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} required />
                </div>
                <div>
                  <p className="text-sm font-medium mb-1">New password</p>
                  <Input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} required minLength={8} />
                </div>
                <div>
                  <p className="text-sm font-medium mb-1">Confirm new password</p>
                  <Input type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} required />
                </div>
                <Button type="submit"><Lock className="h-4 w-4 mr-2" /> Change Password</Button>
              </form>
            </div>

            <hr className="border-border" />

            <div>
              <h3 className="font-medium text-destructive mb-2">Danger Zone</h3>
              <p className="text-sm text-muted-foreground mb-3">
                Once you delete your account, all your documents and data will be permanently removed.
              </p>
              <Button variant="destructive" onClick={handleDeleteAccount}>
                <Trash2 className="h-4 w-4 mr-2" /> Delete Account
              </Button>
            </div>
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
