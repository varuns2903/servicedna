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
import { useChangePassword, useRequestEmailChange } from '@/hooks/useAuth';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Input } from '@/components/ui/Input';
import { Building2, Users, CreditCard, Send, Check, X, UserCog, Phone, FileText, AlertTriangle } from 'lucide-react';
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
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [emailChangePassword, setEmailChangePassword] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [emailChangeError, setEmailChangeError] = useState<string | null>(null);

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
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-white">Settings</h1>
      </div>

      <div className="flex space-x-4 border-b border-charcoal-700">
        <button
          onClick={() => setActiveTab('general')}
          className={`pb-3 text-sm font-medium transition-colors border-b-2 ${
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
          className={`pb-3 text-sm font-medium transition-colors border-b-2 ${
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
          className={`pb-3 text-sm font-medium transition-colors border-b-2 ${
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
          className={`pb-3 text-sm font-medium transition-colors border-b-2 ${
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
          className={`pb-3 text-sm font-medium transition-colors border-b-2 ${
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
          className={`pb-3 text-sm font-medium transition-colors border-b-2 ${
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
          className={`pb-3 text-sm font-medium transition-colors border-b-2 ${
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
                <form onSubmit={handleSendInvite} className="flex items-end space-x-4">
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
                  <div className="w-48">
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
                  <Button type="submit" disabled={createInvite.isPending} className="flex items-center space-x-2">
                    <Send className="h-4 w-4" />
                    <span>Send Invite</span>
                  </Button>
                </form>
              </CardContent>
            </Card>

            <div className="grid grid-cols-2 gap-6">
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
                <div className="flex items-center justify-between">
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

            <div className="grid grid-cols-3 gap-6">
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
          </div>
        )}

        {activeTab === 'audit' && currentOrgId && <AuditLogTab orgId={currentOrgId} />}
      </div>
    </div>
  );
}
