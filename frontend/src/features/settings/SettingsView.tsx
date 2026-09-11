import { useState, useEffect } from 'react';
import axios from 'axios';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import {
  useOrganizations,
  useUpdateOrganization,
  useOrganizationMembers,
  useOrganizationInvites,
  useCreateInvite,
  useUpdateMemberRole,
  useRemoveMember,
} from '@/hooks/useOrganizations';
import { useUser } from '@/hooks/useUser';
import { useSubscription, useCreateCheckoutSession } from '@/hooks/useBilling';
import {
  useChangePassword,
  useRequestEmailChange,
  useNotificationPreferences,
  useUpdateNotificationPreferences,
  useExportMyData,
  useDeleteAccount,
} from '@/hooks/useAuth';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Input } from '@/components/ui/Input';
import { Building2, Users, CreditCard, Send, Check, X, UserCog, Phone, FileText, AlertTriangle, Download } from 'lucide-react';
import type { OrganizationRole } from '@/api/organizations.api';
import type { PlanType } from '@/api/billing.api';
import { OnCallTab } from './OnCallTab';
import { EscalationTab } from './EscalationTab';
import { AuditLogTab } from './AuditLogTab';

export function SettingsView() {
  const currentOrgId = useOrganizationStore((state) => state.selectedOrganizationId);
  const { data: orgs } = useOrganizations();
  const currentOrg = orgs?.find((o) => o.id === currentOrgId);

  const [activeTab, setActiveTab] = useState<'general' | 'members' | 'billing' | 'account' | 'oncall' | 'escalation' | 'audit'>('general');

  // General tab state
  const [orgName, setOrgName] = useState('');
  const updateOrg = useUpdateOrganization();

  useEffect(() => {
    if (currentOrg) {
      setOrgName(currentOrg.name);
    }
  }, [currentOrg]);

  const handleUpdateName = (e: React.FormEvent) => {
    e.preventDefault();
    if (currentOrgId && orgName) {
      updateOrg.mutate({ orgId: currentOrgId, name: orgName });
    }
  };

  // Members Tab state
  const { data: currentUser } = useUser();
  const { data: members, isLoading: loadingMembers } = useOrganizationMembers(currentOrgId || undefined);
  const { data: invites, isLoading: loadingInvites } = useOrganizationInvites(currentOrgId || undefined);
  const createInvite = useCreateInvite();
  const updateMemberRole = useUpdateMemberRole();
  const removeMember = useRemoveMember();
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<OrganizationRole>('MEMBER');
  const [memberActionError, setMemberActionError] = useState<string | null>(null);

  const handleRoleChange = (memberId: string, role: OrganizationRole) => {
    if (!currentOrgId) return;
    setMemberActionError(null);
    updateMemberRole.mutate(
      { orgId: currentOrgId, memberId, role },
      {
        onError: (err) => {
          setMemberActionError(
            (axios.isAxiosError(err) && err.response?.data?.message) || 'Could not update role.'
          );
        },
      }
    );
  };

  const handleRemoveMember = (memberId: string) => {
    if (!currentOrgId) return;
    setMemberActionError(null);
    removeMember.mutate(
      { orgId: currentOrgId, memberId },
      {
        onError: (err) => {
          setMemberActionError(
            (axios.isAxiosError(err) && err.response?.data?.message) || 'Could not remove member.'
          );
        },
      }
    );
  };

  const handleSendInvite = (e: React.FormEvent) => {
    e.preventDefault();
    if (currentOrgId && inviteEmail) {
      createInvite.mutate(
        { orgId: currentOrgId, email: inviteEmail, role: inviteRole },
        {
          onSuccess: () => {
            setInviteEmail('');
            setInviteRole('MEMBER');
          }
        }
      );
    }
  };

  // Account Tab state
  const changePassword = useChangePassword();
  const requestEmailChange = useRequestEmailChange();
  const { data: notificationPreferences } = useNotificationPreferences();
  const updateNotificationPreferences = useUpdateNotificationPreferences();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [emailChangePassword, setEmailChangePassword] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [emailChangeError, setEmailChangeError] = useState<string | null>(null);
  const exportMyData = useExportMyData();
  const deleteAccount = useDeleteAccount();
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const handleExportData = () => {
    exportMyData.mutate(undefined, {
      onSuccess: (data) => {
        const blob = new Blob([JSON.stringify(data, null, 2)], {
          type: 'application/json;charset=utf-8;',
        });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `servicedna-my-data-${new Date().toISOString().slice(0, 10)}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
      },
    });
  };

  const handleDeleteAccount = (e: React.FormEvent) => {
    e.preventDefault();
    setDeleteError(null);
    deleteAccount.mutate(deletePassword, {
      onError: (err) => {
        setDeleteError(
          (axios.isAxiosError(err) && err.response?.data?.message) || 'Could not delete account.'
        );
      },
    });
  };

  const handleChangePassword = (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);
    changePassword.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          setCurrentPassword('');
          setNewPassword('');
        },
        onError: (err) => {
          setPasswordError(
            (axios.isAxiosError(err) && err.response?.data?.message) || 'Could not change password.'
          );
        },
      }
    );
  };

  const handleRequestEmailChange = (e: React.FormEvent) => {
    e.preventDefault();
    setEmailChangeError(null);
    requestEmailChange.mutate(
      { password: emailChangePassword, newEmail },
      {
        onSuccess: () => {
          setEmailChangePassword('');
          setNewEmail('');
        },
        onError: (err) => {
          setEmailChangeError(
            (axios.isAxiosError(err) && err.response?.data?.message) || 'Could not request email change.'
          );
        },
      }
    );
  };

  // Billing Tab state
  const { data: subscription, isLoading: loadingSubscription } = useSubscription(currentOrgId || undefined);
  const createCheckout = useCreateCheckoutSession();

  const handleUpgrade = (plan: PlanType) => {
    if (currentOrgId) {
      createCheckout.mutate(
        { orgId: currentOrgId, planType: plan },
        {
          onSuccess: (data) => {
            window.location.href = data.url;
          }
        }
      );
    }
  };

  if (!currentOrg) return <div className="p-8 text-gray-400">Loading settings...</div>;

  return (
    <div className="p-4 space-y-6 md:p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-white">Settings</h1>
      </div>

      <div className="flex space-x-4 overflow-x-auto border-b border-charcoal-700 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        <button
          onClick={() => setActiveTab('general')}
          className={`shrink-0 pb-3 text-sm font-medium transition-colors border-b-2 ${
            activeTab === 'general' ? 'border-emerald-500 text-white' : 'border-transparent text-gray-400 hover:text-gray-300'
          }`}
        >
          <div className="flex items-center space-x-2">
            <Building2 className="h-4 w-4" />
            <span>General</span>
          </div>
        </button>
        <button
          onClick={() => setActiveTab('members')}
          className={`shrink-0 pb-3 text-sm font-medium transition-colors border-b-2 ${
            activeTab === 'members' ? 'border-emerald-500 text-white' : 'border-transparent text-gray-400 hover:text-gray-300'
          }`}
        >
          <div className="flex items-center space-x-2">
            <Users className="h-4 w-4" />
            <span>Members & Invites</span>
          </div>
        </button>
        <button
          onClick={() => setActiveTab('oncall')}
          className={`shrink-0 pb-3 text-sm font-medium transition-colors border-b-2 ${
            activeTab === 'oncall' ? 'border-emerald-500 text-white' : 'border-transparent text-gray-400 hover:text-gray-300'
          }`}
        >
          <div className="flex items-center space-x-2">
            <Phone className="h-4 w-4" />
            <span>On-Call</span>
          </div>
        </button>
        <button
          onClick={() => setActiveTab('escalation')}
          className={`shrink-0 pb-3 text-sm font-medium transition-colors border-b-2 ${
            activeTab === 'escalation' ? 'border-emerald-500 text-white' : 'border-transparent text-gray-400 hover:text-gray-300'
          }`}
        >
          <div className="flex items-center space-x-2">
            <AlertTriangle className="h-4 w-4" />
            <span>Escalation</span>
          </div>
        </button>
        <button
          onClick={() => setActiveTab('billing')}
          className={`shrink-0 pb-3 text-sm font-medium transition-colors border-b-2 ${
            activeTab === 'billing' ? 'border-emerald-500 text-white' : 'border-transparent text-gray-400 hover:text-gray-300'
          }`}
        >
          <div className="flex items-center space-x-2">
            <CreditCard className="h-4 w-4" />
            <span>Billing</span>
          </div>
        </button>
        <button
          onClick={() => setActiveTab('account')}
          className={`shrink-0 pb-3 text-sm font-medium transition-colors border-b-2 ${
            activeTab === 'account' ? 'border-emerald-500 text-white' : 'border-transparent text-gray-400 hover:text-gray-300'
          }`}
        >
          <div className="flex items-center space-x-2">
            <UserCog className="h-4 w-4" />
            <span>Account</span>
          </div>
        </button>
        <button
          onClick={() => setActiveTab('audit')}
          className={`shrink-0 pb-3 text-sm font-medium transition-colors border-b-2 ${
            activeTab === 'audit' ? 'border-emerald-500 text-white' : 'border-transparent text-gray-400 hover:text-gray-300'
          }`}
        >
          <div className="flex items-center space-x-2">
            <FileText className="h-4 w-4" />
            <span>Audit Log</span>
          </div>
        </button>
      </div>

      <div className="pt-4">
        {activeTab === 'general' && (
          <Card className="max-w-2xl">
            <CardHeader>
              <CardTitle>Organization Name</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleUpdateName} className="space-y-4">
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Name</label>
                  <input
                    type="text"
                    value={orgName}
                    onChange={(e) => setOrgName(e.target.value)}
                    className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                  />
                </div>
                <div className="flex justify-end">
                  <Button type="submit" disabled={updateOrg.isPending || orgName === currentOrg.name}>
                    {updateOrg.isPending ? 'Saving...' : 'Save Changes'}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        )}

        {activeTab === 'members' && (
          <div className="space-y-6 max-w-4xl">
            <Card>
              <CardHeader>
                <CardTitle>Invite New Member</CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleSendInvite} className="flex flex-col gap-4 sm:flex-row sm:items-end sm:gap-4">
                  <div className="flex-1">
                    <label className="block text-sm text-gray-400 mb-1">Email Address</label>
                    <input
                      type="email"
                      required
                      value={inviteEmail}
                      onChange={(e) => setInviteEmail(e.target.value)}
                      placeholder="colleague@example.com"
                      className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                    />
                  </div>
                  <div className="sm:w-48">
                    <label className="block text-sm text-gray-400 mb-1">Role</label>
                    <select
                      value={inviteRole}
                      onChange={(e) => setInviteRole(e.target.value as OrganizationRole)}
                      className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                    >
                      <option value="ADMIN">Admin</option>
                      <option value="MEMBER">Member</option>
                      <option value="VIEWER">Viewer</option>
                    </select>
                  </div>
                  <Button type="submit" disabled={createInvite.isPending} className="flex items-center justify-center space-x-2">
                    <Send className="h-4 w-4" />
                    <span>Send Invite</span>
                  </Button>
                </form>
              </CardContent>
            </Card>

            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle>Active Members</CardTitle>
                </CardHeader>
                <CardContent>
                  {memberActionError && (
                    <div className="mb-3 rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
                      {memberActionError}
                    </div>
                  )}
                  {loadingMembers ? (
                    <div className="text-sm text-gray-400">Loading members...</div>
                  ) : (
                    <div className="space-y-3">
                      {members?.map(m => {
                        const isSelf = m.email === currentUser?.email;
                        return (
                          <div key={m.id} className="flex items-center justify-between gap-2 p-3 rounded-md border border-charcoal-700 bg-charcoal-900/50">
                            <div className="flex flex-col min-w-0">
                              <span className="text-sm text-white truncate">{m.email}</span>
                              {isSelf && <span className="text-xs text-gray-500">You</span>}
                            </div>
                            <div className="flex items-center space-x-2 shrink-0">
                              {m.role === 'OWNER' ? (
                                <Badge variant="success">OWNER</Badge>
                              ) : (
                                <select
                                  value={m.role}
                                  onChange={(e) => handleRoleChange(m.id, e.target.value as OrganizationRole)}
                                  disabled={updateMemberRole.isPending}
                                  className="bg-charcoal-900 border border-charcoal-700 rounded-md px-2 py-1 text-xs text-white"
                                >
                                  <option value="ADMIN">Admin</option>
                                  <option value="MEMBER">Member</option>
                                  <option value="VIEWER">Viewer</option>
                                </select>
                              )}
                              <button
                                onClick={() => handleRemoveMember(m.id)}
                                disabled={removeMember.isPending}
                                title={isSelf ? 'Leave organization' : 'Remove member'}
                                className="flex h-7 w-7 items-center justify-center rounded-md text-gray-500 transition-colors hover:bg-rose-500/10 hover:text-rose-400"
                              >
                                <X className="h-4 w-4" />
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Pending Invites</CardTitle>
                </CardHeader>
                <CardContent>
                  {loadingInvites ? (
                    <div className="text-sm text-gray-400">Loading invites...</div>
                  ) : invites?.length === 0 ? (
                    <div className="text-sm text-gray-500">No pending invites.</div>
                  ) : (
                    <div className="space-y-3">
                      {invites?.filter(i => i.status === 'PENDING').map(i => (
                        <div key={i.id} className="flex flex-col space-y-1 p-3 rounded-md border border-charcoal-700 bg-charcoal-900/50">
                          <div className="flex items-center justify-between">
                            <span className="text-sm text-white">{i.email}</span>
                            <Badge variant="warning">Pending</Badge>
                          </div>
                          <div className="flex items-center justify-between text-xs text-gray-500">
                            <span>Role: {i.role}</span>
                            <span>Expires {new Date(i.expiresAt).toLocaleDateString()}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          </div>
        )}

        {activeTab === 'oncall' && currentOrgId && <OnCallTab orgId={currentOrgId} members={members} />}

        {activeTab === 'escalation' && currentOrgId && <EscalationTab orgId={currentOrgId} />}

        {activeTab === 'billing' && (
          <div className="space-y-6 max-w-5xl">
            <Card className="border-emerald-500/30">
              <CardContent className="p-6">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <h3 className="text-lg font-medium text-white mb-1">Current Subscription</h3>
                    {loadingSubscription ? (
                      <p className="text-sm text-gray-400">Loading...</p>
                    ) : (
                      <div className="flex items-center space-x-3">
                        <Badge variant={subscription?.planType === 'FREE' ? 'default' : 'success'} className="text-base py-1">
                          {subscription?.planType || 'FREE'} PLAN
                        </Badge>
                        <span className="text-sm text-gray-400">Status: {subscription?.status || 'ACTIVE'}</span>
                      </div>
                    )}
                  </div>
                  <Button variant="outline" onClick={() => window.open('https://billing.stripe.com/p/session/test_abc123', '_blank')}>
                    Manage Billing Portal
                  </Button>
                </div>
              </CardContent>
            </Card>

            <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
              <Card className="flex flex-col">
                <CardHeader>
                  <CardTitle className="text-xl">Free</CardTitle>
                  <p className="text-sm text-gray-400 mt-2">$0 / month</p>
                </CardHeader>
                <CardContent className="flex-1 flex flex-col justify-between">
                  <ul className="space-y-3 text-sm text-gray-300 mb-6">
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>3 Services max</span></li>
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>1 Day Data Retention</span></li>
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>Community Support</span></li>
                  </ul>
                  <Button disabled variant="outline" className="w-full text-gray-500 border-gray-700">Current Plan</Button>
                </CardContent>
              </Card>

              <Card className="flex flex-col border-emerald-500/50 relative overflow-hidden">
                <div className="absolute top-0 right-0 bg-emerald-500 text-charcoal-900 text-[10px] font-bold px-3 py-1 uppercase rounded-bl-lg">Recommended</div>
                <CardHeader>
                  <CardTitle className="text-xl">Pro</CardTitle>
                  <p className="text-sm text-gray-400 mt-2">$49 / month</p>
                </CardHeader>
                <CardContent className="flex-1 flex flex-col justify-between">
                  <ul className="space-y-3 text-sm text-gray-300 mb-6">
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>50 Services max</span></li>
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>30 Days Data Retention</span></li>
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>Email Support</span></li>
                  </ul>
                  <Button 
                    className="w-full"
                    onClick={() => handleUpgrade('PRO')}
                    disabled={createCheckout.isPending || subscription?.planType === 'PRO'}
                  >
                    {createCheckout.isPending ? 'Processing...' : 'Upgrade to Pro'}
                  </Button>
                </CardContent>
              </Card>

              <Card className="flex flex-col">
                <CardHeader>
                  <CardTitle className="text-xl">Enterprise</CardTitle>
                  <p className="text-sm text-gray-400 mt-2">Custom Pricing</p>
                </CardHeader>
                <CardContent className="flex-1 flex flex-col justify-between">
                  <ul className="space-y-3 text-sm text-gray-300 mb-6">
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>Unlimited Services</span></li>
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>Unlimited Data Retention</span></li>
                    <li className="flex items-center space-x-2"><Check className="h-4 w-4 text-emerald-500"/><span>24/7 Phone Support</span></li>
                  </ul>
                  <Button 
                    variant="outline" 
                    className="w-full"
                    onClick={() => handleUpgrade('ENTERPRISE')}
                    disabled={createCheckout.isPending || subscription?.planType === 'ENTERPRISE'}
                  >
                    Contact Sales
                  </Button>
                </CardContent>
              </Card>
            </div>
          </div>
        )}

        {activeTab === 'account' && (
          <div className="space-y-6 max-w-2xl">
            <Card>
              <CardHeader>
                <CardTitle>Change Password</CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleChangePassword} className="space-y-4">
                  <div>
                    <label className="block text-sm text-gray-400 mb-1">Current Password</label>
                    <Input
                      type="password"
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-400 mb-1">New Password</label>
                    <Input
                      type="password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      minLength={8}
                      required
                    />
                  </div>
                  {passwordError && (
                    <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
                      {passwordError}
                    </div>
                  )}
                  {changePassword.isSuccess && !passwordError && (
                    <div className="rounded-md border border-emerald-500/20 bg-emerald-500/10 p-2 text-xs text-emerald-400">
                      Password updated.
                    </div>
                  )}
                  <div className="flex justify-end">
                    <Button type="submit" disabled={changePassword.isPending || !currentPassword || newPassword.length < 8}>
                      {changePassword.isPending ? 'Saving...' : 'Change Password'}
                    </Button>
                  </div>
                </form>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Change Email</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-gray-400 mb-4">
                  Current email: <span className="text-gray-200">{currentUser?.email}</span>
                </p>
                <form onSubmit={handleRequestEmailChange} className="space-y-4">
                  <div>
                    <label className="block text-sm text-gray-400 mb-1">New Email</label>
                    <Input
                      type="email"
                      value={newEmail}
                      onChange={(e) => setNewEmail(e.target.value)}
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-400 mb-1">Password</label>
                    <Input
                      type="password"
                      value={emailChangePassword}
                      onChange={(e) => setEmailChangePassword(e.target.value)}
                      required
                    />
                  </div>
                  {emailChangeError && (
                    <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
                      {emailChangeError}
                    </div>
                  )}
                  {requestEmailChange.isSuccess && !emailChangeError && (
                    <div className="rounded-md border border-emerald-500/20 bg-emerald-500/10 p-2 text-xs text-emerald-400">
                      Check {newEmail || 'your new inbox'} for a confirmation link to finish the change.
                    </div>
                  )}
                  <div className="flex justify-end">
                    <Button type="submit" disabled={requestEmailChange.isPending || !newEmail || !emailChangePassword}>
                      {requestEmailChange.isPending ? 'Sending...' : 'Send Confirmation'}
                    </Button>
                  </div>
                </form>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Notification Preferences</CardTitle>
              </CardHeader>
              <CardContent>
                <label className="flex items-center justify-between">
                  <div>
                    <p className="text-sm text-gray-200">Email me about new incidents</p>
                    <p className="text-xs text-gray-500">
                      Get notified whenever any incident is reported in your organizations, regardless
                      of severity or whether you're on call.
                    </p>
                  </div>
                  <input
                    type="checkbox"
                    className="ml-4 h-5 w-5 shrink-0 rounded border-charcoal-600 bg-charcoal-900 text-emerald-500 focus:ring-emerald-500"
                    checked={notificationPreferences?.notifyOnNewIncident || false}
                    onChange={(e) =>
                      updateNotificationPreferences.mutate({ notifyOnNewIncident: e.target.checked })
                    }
                    disabled={updateNotificationPreferences.isPending}
                  />
                </label>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Your Data</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm text-gray-200">Export my data</p>
                    <p className="text-xs text-gray-500">
                      Download a JSON file with your account details, organization memberships, and
                      incidents you've reported.
                    </p>
                  </div>
                  <Button
                    variant="outline"
                    onClick={handleExportData}
                    disabled={exportMyData.isPending}
                    className="flex items-center space-x-2 shrink-0 ml-4"
                  >
                    <Download className="h-4 w-4" />
                    <span>{exportMyData.isPending ? 'Preparing...' : 'Export Data'}</span>
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card className="border-rose-500/30">
              <CardHeader>
                <CardTitle className="text-rose-400">Danger Zone</CardTitle>
              </CardHeader>
              <CardContent>
                {!showDeleteConfirm ? (
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm text-gray-200">Delete account</p>
                      <p className="text-xs text-gray-500">
                        Permanently deactivates your account and removes you from all organizations.
                        This cannot be undone.
                      </p>
                    </div>
                    <Button
                      variant="outline"
                      onClick={() => setShowDeleteConfirm(true)}
                      className="border-rose-500/40 text-rose-400 hover:bg-rose-500/10 shrink-0 ml-4"
                    >
                      Delete Account
                    </Button>
                  </div>
                ) : (
                  <form onSubmit={handleDeleteAccount} className="space-y-4">
                    <p className="text-sm text-gray-300">
                      This will permanently deactivate your account. If you're the sole owner of any
                      organization, you'll need to transfer ownership or delete it first. Enter your
                      password to confirm.
                    </p>
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Password</label>
                      <Input
                        type="password"
                        value={deletePassword}
                        onChange={(e) => setDeletePassword(e.target.value)}
                        required
                      />
                    </div>
                    {deleteError && (
                      <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
                        {deleteError}
                      </div>
                    )}
                    <div className="flex justify-end space-x-3">
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => {
                          setShowDeleteConfirm(false);
                          setDeletePassword('');
                          setDeleteError(null);
                        }}
                      >
                        Cancel
                      </Button>
                      <Button
                        type="submit"
                        disabled={deleteAccount.isPending || !deletePassword}
                        className="bg-rose-600 hover:bg-rose-500 text-white"
                      >
                        {deleteAccount.isPending ? 'Deleting...' : 'Permanently Delete Account'}
                      </Button>
                    </div>
                  </form>
                )}
              </CardContent>
            </Card>
          </div>
        )}

        {activeTab === 'audit' && currentOrgId && <AuditLogTab orgId={currentOrgId} />}
      </div>
    </div>
  );
}
