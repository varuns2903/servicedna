
import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Server, Network, AlertTriangle, BellRing, Settings, Activity, ChevronLeft, ChevronRight } from 'lucide-react';
import { useUIStore } from '@/stores/useUIStore';
import { cn } from '@/utils/cn';

const navItems = [
  { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Services', path: '/services', icon: Server },
  { name: 'Dependency Graph', path: '/map', icon: Network },
  { name: 'Incidents', path: '/incidents', icon: AlertTriangle },
  { name: 'Alerts', path: '/alerts', icon: BellRing },
  { name: 'Settings', path: '/settings', icon: Settings },
];

export function Sidebar() {
  const { sidebarCollapsed, toggleSidebar } = useUIStore();

  return (
    <aside
      className={cn(
        'relative flex h-full flex-col border-r border-charcoal-700 bg-charcoal-800 transition-all duration-300',
        sidebarCollapsed ? 'w-16' : 'w-64'
      )}
    >
      <div className="flex h-14 items-center justify-between border-b border-charcoal-700 px-4">
        <div className="flex items-center gap-2 overflow-hidden whitespace-nowrap">
          <Activity className="h-6 w-6 shrink-0 text-emerald-400" />
          {!sidebarCollapsed && <span className="font-semibold tracking-tight text-white">ServiceDNA</span>}
        </div>
      </div>

      <nav className="flex-1 space-y-1 p-2 overflow-y-auto">
        {navItems.map((item) => (
          <NavLink
            key={item.name}
            to={item.path}
            className={({ isActive }) =>
              cn(
                'group flex items-center rounded-md px-2 py-2 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-charcoal-700 text-white'
                  : 'text-gray-400 hover:bg-charcoal-700/50 hover:text-white'
              )
            }
            title={sidebarCollapsed ? item.name : undefined}
          >
            <item.icon className={cn('h-5 w-5 shrink-0', sidebarCollapsed ? 'mx-auto' : 'mr-3')} />
            {!sidebarCollapsed && <span>{item.name}</span>}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-charcoal-700 p-2">
        <button
          onClick={toggleSidebar}
          className="flex w-full items-center justify-center rounded-md p-2 text-gray-400 transition-colors hover:bg-charcoal-700 hover:text-white"
        >
          {sidebarCollapsed ? <ChevronRight className="h-5 w-5" /> : <ChevronLeft className="h-5 w-5" />}
        </button>
      </div>
    </aside>
  );
}
