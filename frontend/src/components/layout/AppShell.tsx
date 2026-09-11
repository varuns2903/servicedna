import * as React from 'react';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';
import { CommandPalette } from "@/components/ui/CommandPalette";

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <>
      <CommandPalette />
      <div className="flex h-screen w-screen overflow-hidden bg-charcoal-900 text-gray-100">
        <Sidebar />
        <div className="flex flex-1 flex-col overflow-hidden">
          <TopBar />
          <main className="flex-1 overflow-auto p-4 md:p-6">
            <div className="mx-auto h-full max-w-7xl">
              {children}
            </div>
          </main>
        </div>
      </div>
    </>
  );
}
