import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Command } from 'cmdk';
import { useUIStore } from '@/stores/useUIStore';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { useServices } from '@/hooks/useServices';
import { useIncidents } from '@/hooks/useIncidents';
import { Search, Server, AlertTriangle, Settings, LayoutDashboard, Share2 } from 'lucide-react';

export function CommandPalette() {
  const { isCommandPaletteOpen, setCommandPaletteOpen, toggleCommandPalette } = useUIStore();
  const navigate = useNavigate();
  const orgId = useOrganizationStore((state) => state.selectedOrganizationId);
  
  // These hooks already use the orgId from the store internally
  const { data: services } = useServices();
  const { data: incidents } = useIncidents(orgId || undefined);
  
  const [searchValue, setSearchValue] = useState('');

  // Toggle the menu when ⌘K is pressed
  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.key === 'k' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        toggleCommandPalette();
      }
    };

    document.addEventListener('keydown', down);
    return () => document.removeEventListener('keydown', down);
  }, [toggleCommandPalette]);

  const runCommand = (command: () => void) => {
    setCommandPaletteOpen(false);
    command();
    setSearchValue('');
  };

  if (!isCommandPaletteOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[20vh]">
      <div 
        className="fixed inset-0 bg-charcoal-900/60 backdrop-blur-sm" 
        onClick={() => setCommandPaletteOpen(false)}
      />
      
      <div className="relative w-full max-w-2xl bg-charcoal-800 border border-charcoal-700 rounded-xl shadow-2xl overflow-hidden flex flex-col">
        <Command 
          className="flex flex-col w-full h-full"
          value={searchValue}
          onValueChange={setSearchValue}
        >
          <div className="flex items-center border-b border-charcoal-700 px-4 py-3">
            <Search className="w-5 h-5 text-gray-400 mr-3" />
            <Command.Input 
              autoFocus
              placeholder="Search services, incidents, or jump to..." 
              className="flex-1 bg-transparent border-none text-white placeholder-gray-500 focus:outline-none focus:ring-0 text-lg" 
            />
            <div className="text-xs text-gray-500 font-mono bg-charcoal-900 px-2 py-1 rounded border border-charcoal-700">ESC</div>
          </div>

          <Command.List className="max-h-[300px] overflow-y-auto p-2 scrollbar-thin scrollbar-thumb-charcoal-600 scrollbar-track-transparent">
            <Command.Empty className="py-6 text-center text-gray-400 text-sm">No results found.</Command.Empty>

            <Command.Group heading="Navigation" className="text-xs font-semibold text-gray-500 px-2 py-1 uppercase tracking-wider">
              <Command.Item 
                onSelect={() => runCommand(() => navigate('/dashboard'))}
                className="flex items-center px-3 py-2 text-sm text-gray-200 rounded-md cursor-pointer hover:bg-emerald-500/10 hover:text-emerald-400 aria-selected:bg-emerald-500/10 aria-selected:text-emerald-400"
              >
                <LayoutDashboard className="w-4 h-4 mr-3" /> Dashboard
              </Command.Item>
              <Command.Item 
                onSelect={() => runCommand(() => navigate('/settings'))}
                className="flex items-center px-3 py-2 text-sm text-gray-200 rounded-md cursor-pointer hover:bg-emerald-500/10 hover:text-emerald-400 aria-selected:bg-emerald-500/10 aria-selected:text-emerald-400"
              >
                <Settings className="w-4 h-4 mr-3" /> Settings
              </Command.Item>
              {orgId && (
                <Command.Item 
                  onSelect={() => runCommand(() => window.open(`/status/${orgId}`, '_blank'))}
                  className="flex items-center px-3 py-2 text-sm text-gray-200 rounded-md cursor-pointer hover:bg-emerald-500/10 hover:text-emerald-400 aria-selected:bg-emerald-500/10 aria-selected:text-emerald-400"
                >
                  <Share2 className="w-4 h-4 mr-3" /> View Public Status Page
                </Command.Item>
              )}
            </Command.Group>

            {services && services.length > 0 && (
              <Command.Group heading="Services" className="text-xs font-semibold text-gray-500 px-2 py-3 uppercase tracking-wider border-t border-charcoal-700 mt-2">
                {services.map((service) => (
                  <Command.Item
                    key={service.id}
                    value={`service ${service.name}`}
                    onSelect={() => runCommand(() => navigate(`/services`))}
                    className="flex items-center px-3 py-2 text-sm text-gray-200 rounded-md cursor-pointer hover:bg-emerald-500/10 hover:text-emerald-400 aria-selected:bg-emerald-500/10 aria-selected:text-emerald-400"
                  >
                    <Server className="w-4 h-4 mr-3" /> {service.name}
                  </Command.Item>
                ))}
              </Command.Group>
            )}

            {incidents && incidents.length > 0 && (
              <Command.Group heading="Incidents" className="text-xs font-semibold text-gray-500 px-2 py-3 uppercase tracking-wider border-t border-charcoal-700 mt-2">
                {incidents.map((incident) => (
                  <Command.Item
                    key={incident.id}
                    value={`incident ${incident.title}`}
                    onSelect={() => runCommand(() => navigate(`/incidents/${incident.id}`))}
                    className="flex items-center px-3 py-2 text-sm text-gray-200 rounded-md cursor-pointer hover:bg-emerald-500/10 hover:text-emerald-400 aria-selected:bg-emerald-500/10 aria-selected:text-emerald-400"
                  >
                    <AlertTriangle className="w-4 h-4 mr-3 text-amber-500" /> {incident.title}
                  </Command.Item>
                ))}
              </Command.Group>
            )}
          </Command.List>
        </Command>
      </div>
    </div>
  );
}
