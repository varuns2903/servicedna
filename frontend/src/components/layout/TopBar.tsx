import { Search, LogOut, Wifi, WifiOff } from 'lucide-react';
import { useAuthStore } from '@/stores/useAuthStore';
import { useNavigate } from 'react-router-dom';
import { useUser } from '@/hooks/useUser';
import { useOrganizations } from '@/hooks/useOrganizations';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { useWebSocket } from '@/providers/WebSocketProvider';
import { useUIStore } from "@/stores/useUIStore";

export function TopBar() {
  const { logout } = useAuthStore();
  const navigate = useNavigate();
  const { data: user } = useUser();
  const { data: organizations } = useOrganizations();
  const { selectedOrganizationId, setSelectedOrganizationId } = useOrganizationStore();
  const { connected } = useWebSocket();
  const { toggleCommandPalette } = useUIStore();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <header className="flex h-14 items-center justify-between border-b border-charcoal-700 bg-charcoal-900/80 px-4 backdrop-blur-md">
      <div className="flex flex-1 items-center space-x-4">
        {/* Organization Selector */}
        {organizations && organizations.length > 0 && (
          <select
            value={selectedOrganizationId || ''}
            onChange={(e) => setSelectedOrganizationId(e.target.value)}
            className="flex h-8 items-center rounded-md border border-charcoal-700 bg-charcoal-800 px-2 py-1 text-sm text-gray-200 outline-none focus:border-charcoal-600 focus:ring-1 focus:ring-emerald-500/50"
          >
            {organizations.map((org) => (
              <option key={org.id} value={org.id}>
                {org.name}
              </option>
            ))}
          </select>
        )}
      </div>

      <div className="flex items-center space-x-4">
        {/* Global Search Trigger */}
        <button onClick={toggleCommandPalette} className="flex h-8 w-64 items-center space-x-2 rounded-md border border-charcoal-700 bg-charcoal-800 px-3 text-sm text-gray-400 transition-colors hover:border-charcoal-600 focus:outline-none focus:ring-2 focus:ring-emerald-500/50">
          <Search className="h-4 w-4" />
          <span>Search resources... (Ctrl+K)</span>
        </button>

        {/* Real-time Status */}
        <div className="flex items-center space-x-2 border-l border-charcoal-700 pl-4" title={connected ? "Real-time updates active" : "Disconnected from real-time updates"}>
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
        <div className="flex items-center space-x-3 border-l border-charcoal-700 pl-4">
          <span className="text-sm font-medium text-gray-300">
            {user?.email}
          </span>
          <button 
            onClick={handleLogout}
            className="flex h-8 w-8 items-center justify-center rounded-full bg-charcoal-700 text-gray-300 transition-colors hover:text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
            title="Log out"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </header>
  );
}
