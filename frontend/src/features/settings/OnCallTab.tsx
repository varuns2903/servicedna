import { useEffect, useState } from 'react';
import axios from 'axios';
import { ArrowUp, ArrowDown, X, Phone } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { useOnCallRotation, useUpsertOnCallRotation } from '@/hooks/useOnCall';
import type { OrganizationMemberDto } from '@/api/organizations.api';

interface OnCallTabProps {
  orgId: string;
  members: OrganizationMemberDto[] | undefined;
}

export function OnCallTab({ orgId, members }: OnCallTabProps) {
  const { data: rotation, isLoading } = useOnCallRotation(orgId);
  const upsertRotation = useUpsertOnCallRotation();

  const [rotationLengthDays, setRotationLengthDays] = useState(7);
  const [startDate, setStartDate] = useState('');
  const [orderedMemberIds, setOrderedMemberIds] = useState<string[]>([]);
  const [addMemberId, setAddMemberId] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (rotation) {
      setRotationLengthDays(rotation.rotationLengthDays);
      setStartDate(rotation.startDate);
      setOrderedMemberIds(rotation.members.map((m) => m.organizationMemberId));
    }
  }, [rotation]);

  const membersById = new Map((members || []).map((m) => [m.id, m]));
  const availableMembers = (members || []).filter((m) => !orderedMemberIds.includes(m.id));

  const moveUp = (index: number) => {
    if (index === 0) return;
    const next = [...orderedMemberIds];
    [next[index - 1], next[index]] = [next[index], next[index - 1]];
    setOrderedMemberIds(next);
  };

  const moveDown = (index: number) => {
    if (index === orderedMemberIds.length - 1) return;
    const next = [...orderedMemberIds];
    [next[index + 1], next[index]] = [next[index], next[index + 1]];
    setOrderedMemberIds(next);
  };

  const removeMember = (id: string) => {
    setOrderedMemberIds(orderedMemberIds.filter((m) => m !== id));
  };

  const addMember = () => {
    if (!addMemberId) return;
    setOrderedMemberIds([...orderedMemberIds, addMemberId]);
    setAddMemberId('');
  };

  const handleSave = () => {
    setError(null);
    upsertRotation.mutate(
      { orgId, request: { rotationLengthDays, startDate, organizationMemberIds: orderedMemberIds } },
      {
        onError: (err) => {
          setError((axios.isAxiosError(err) && err.response?.data?.message) || 'Could not save rotation.');
        },
      }
    );
  };

  if (isLoading) {
    return <div className="text-gray-400">Loading on-call schedule...</div>;
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <Card>
        <CardContent className="p-6">
          <div className="flex items-center space-x-3">
            <Phone className="h-5 w-5 text-emerald-400" />
            {rotation?.currentOnCall ? (
              <div>
                <p className="text-sm text-gray-400">Currently on call</p>
                <p className="text-lg font-medium text-white">{rotation.currentOnCall.email}</p>
                {rotation.currentShiftEndsOn && (
                  <p className="text-xs text-gray-500">
                    Shift ends {new Date(rotation.currentShiftEndsOn).toLocaleDateString()}
                  </p>
                )}
              </div>
            ) : (
              <p className="text-sm text-gray-400">No on-call rotation configured yet.</p>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Rotation Settings</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="block text-sm text-gray-400 mb-1">Rotation Length (days)</label>
              <input
                type="number"
                min={1}
                value={rotationLengthDays}
                onChange={(e) => setRotationLengthDays(Number(e.target.value))}
                className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white focus:border-emerald-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-400 mb-1">Rotation Start Date</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white focus:border-emerald-500 focus:outline-none"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm text-gray-400 mb-2">Rotation Order</label>
            {orderedMemberIds.length === 0 ? (
              <p className="text-sm text-gray-500">No members added to the rotation yet.</p>
            ) : (
              <ul className="space-y-2">
                {orderedMemberIds.map((id, index) => {
                  const member = membersById.get(id);
                  return (
                    <li
                      key={id}
                      className="flex items-center justify-between rounded-md border border-charcoal-700 bg-charcoal-900 px-3 py-2"
                    >
                      <div className="flex items-center space-x-2">
                        <Badge variant="default">{index + 1}</Badge>
                        <span className="text-sm text-gray-200">{member?.email || id}</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        <button
                          type="button"
                          onClick={() => moveUp(index)}
                          disabled={index === 0}
                          className="rounded p-1 text-gray-400 hover:text-white disabled:opacity-30"
                        >
                          <ArrowUp className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => moveDown(index)}
                          disabled={index === orderedMemberIds.length - 1}
                          className="rounded p-1 text-gray-400 hover:text-white disabled:opacity-30"
                        >
                          <ArrowDown className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => removeMember(id)}
                          className="rounded p-1 text-gray-400 hover:text-rose-400"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {availableMembers.length > 0 && (
            <div className="flex items-end space-x-2">
              <div className="flex-1">
                <label className="block text-sm text-gray-400 mb-1">Add Member</label>
                <select
                  value={addMemberId}
                  onChange={(e) => setAddMemberId(e.target.value)}
                  className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white focus:border-emerald-500 focus:outline-none"
                >
                  <option value="">-- Select a member --</option>
                  {availableMembers.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.email}
                    </option>
                  ))}
                </select>
              </div>
              <Button type="button" variant="outline" onClick={addMember} disabled={!addMemberId}>
                Add
              </Button>
            </div>
          )}

          {error && (
            <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
              {error}
            </div>
          )}

          <div className="flex justify-end">
            <Button
              onClick={handleSave}
              disabled={upsertRotation.isPending || orderedMemberIds.length === 0 || !startDate}
            >
              {upsertRotation.isPending ? 'Saving...' : 'Save Rotation'}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
