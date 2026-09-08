
import { Search, LogOut } from 'lucide-react';
import { useAuthStore } from '@/stores/useAuthStore';
import { useNavigate } from 'react-router-dom';
import { useUser } from '@/hooks/useUser';
import { useOrganizations } from '@/hooks/useOrganizations';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function TopBar() {
  const { logout } = useAuthStore();
  const navigate = useNavigate();
  const { data: user } = useUser();
  const { data: organizations } = useOrganizations();
  const { selectedOrganizationId, setSelectedOrganizationId } = useOrganizationStore();

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
        <button className="flex h-8 w-64 items-center space-x-2 rounded-md border border-charcoal-700 bg-charcoal-800 px-3 text-sm text-gray-400 transition-colors hover:border-charcoal-600 focus:outline-none focus:ring-2 focus:ring-emerald-500/50">
          <Search className="h-4 w-4" />
          <span>Search resources... (Ctrl+K)</span>
        </button>

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
