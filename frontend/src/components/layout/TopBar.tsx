import { useState } from 'react';
import { Search, LogOut, Wifi, WifiOff, Plus, Menu } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useUser } from '@/hooks/useUser';
import { useLogout } from '@/hooks/useAuth';
import { useOrganizations } from '@/hooks/useOrganizations';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { useWebSocket } from '@/providers/WebSocketProvider';
import { useUIStore } from "@/stores/useUIStore";
import { CreateOrganizationModal } from '@/features/organizations/CreateOrganizationModal';

export function TopBar() {
  const logout = useLogout();
  const navigate = useNavigate();
  const { data: user } = useUser();
  const { data: organizations } = useOrganizations();
  const { selectedOrganizationId, setSelectedOrganizationId } = useOrganizationStore();
  const { connected } = useWebSocket();
  const { toggleCommandPalette, toggleMobileSidebar } = useUIStore();
  const [isCreateOrgOpen, setCreateOrgOpen] = useState(false);

  const handleLogout = () => {
    logout.mutate(undefined, { onSettled: () => navigate('/login') });
  };

  return (
    <header className="flex h-14 items-center justify-between gap-2 border-b border-charcoal-700 bg-charcoal-900/80 px-3 backdrop-blur-md md:px-4">
      <div className="flex flex-1 items-center space-x-2 min-w-0 md:space-x-4">
        <button
          onClick={toggleMobileSidebar}
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-gray-400 hover:text-white md:hidden"
          title="Open menu"
        >
          <Menu className="h-5 w-5" />
        </button>

        {/* Organization Selector */}
        {organizations && organizations.length > 0 && (
          <select
            value={selectedOrganizationId || ''}
            onChange={(e) => setSelectedOrganizationId(e.target.value)}
            className="flex h-8 min-w-0 max-w-[9rem] items-center rounded-md border border-charcoal-700 bg-charcoal-800 px-2 py-1 text-sm text-gray-200 outline-none focus:border-charcoal-600 focus:ring-1 focus:ring-emerald-500/50 sm:max-w-none"
          >
            {organizations.map((org) => (
              <option key={org.id} value={org.id}>
                {org.name}
              </option>
            ))}
          </select>
        )}
        <button
          onClick={() => setCreateOrgOpen(true)}
          title="Create Organization"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-charcoal-700 bg-charcoal-800 text-gray-400 transition-colors hover:border-charcoal-600 hover:text-white"
        >
          <Plus className="h-4 w-4" />
        </button>
        <CreateOrganizationModal open={isCreateOrgOpen} onClose={() => setCreateOrgOpen(false)} />
      </div>

      <div className="flex items-center space-x-2 md:space-x-4">
        {/* Global Search Trigger */}
        <button
          onClick={toggleCommandPalette}
          className="flex h-8 w-8 items-center justify-center rounded-md border border-charcoal-700 bg-charcoal-800 text-gray-400 transition-colors hover:border-charcoal-600 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 sm:w-64 sm:justify-start sm:space-x-2 sm:px-3 sm:text-sm"
        >
          <Search className="h-4 w-4 shrink-0" />
          <span className="hidden sm:inline">Search resources... (Ctrl+K)</span>
        </button>

        {/* Real-time Status */}
        <div className="hidden items-center space-x-2 border-l border-charcoal-700 pl-4 sm:flex" title={connected ? "Real-time updates active" : "Disconnected from real-time updates"}>
          {connected ? (
            <Wifi className="h-4 w-4 text-emerald-400" />
          ) : (
            <WifiOff className="h-4 w-4 text-gray-500" />
          )}
          <span className={`text-xs ${connected ? 'text-emerald-400' : 'text-gray-500'}`}>
            {connected ? 'Live' : 'Offline'}
          </span>
        </div>

        {/* User Menu */}
        <div className="flex items-center space-x-2 border-l border-charcoal-700 pl-2 md:space-x-3 md:pl-4">
          <span className="hidden max-w-[10rem] truncate text-sm font-medium text-gray-300 md:inline">
            {user?.email}
          </span>
          <button
            onClick={handleLogout}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-charcoal-700 text-gray-300 transition-colors hover:text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
            title="Log out"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </header>
  );
}
