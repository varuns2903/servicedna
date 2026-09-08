import { useUser } from "@/hooks/useUser";
import { useOrganizations } from "@/hooks/useOrganizations";
import { Loader2 } from "lucide-react";
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Login } from '@/features/auth/Login';
import { OAuth2RedirectHandler } from '@/features/auth/OAuth2RedirectHandler';
import { useAuthStore } from '@/stores/useAuthStore';
import { AppShell } from '@/components/layout/AppShell';
import { Dashboard } from '@/features/dashboard/Dashboard';
import { ServiceList } from '@/features/services/ServiceList';
import { DependencyGraph } from '@/features/map/DependencyGraph';
import { IncidentList } from '@/features/incidents/IncidentList';
import { IncidentDetails } from '@/features/incidents/IncidentDetails';
import { AlertsList } from '@/features/alerts/AlertsList';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60000,
      refetchOnWindowFocus: true,
      retry: 1,
    },
  },
});

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((state) => state.token);
  
  // Queries
  const { isLoading: isUserLoading, isError: isUserError } = useUser();
  const { isLoading: isOrgsLoading, isError: isOrgsError } = useOrganizations();

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (isUserLoading || isOrgsLoading) {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-charcoal-900">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-500" />
      </div>
    );
  }

  if (isUserError || isOrgsError) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler />} />
          <Route 
            path="/dashboard" 
            element={
              <ProtectedRoute>
                <AppShell>
                  <Dashboard />
                </AppShell>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/services" 
            element={
              <ProtectedRoute>
                <AppShell>
                  <ServiceList />
                </AppShell>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/map" 
            element={
              <ProtectedRoute>
                <AppShell>
                  <DependencyGraph />
                </AppShell>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/incidents" 
            element={
              <ProtectedRoute>
                <AppShell>
                  <IncidentList />
                </AppShell>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/incidents/:id" 
            element={
              <ProtectedRoute>
                <AppShell>
                  <IncidentDetails />
                </AppShell>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/alerts" 
            element={
              <ProtectedRoute>
                <AppShell>
                  <AlertsList />
                </AppShell>
              </ProtectedRoute>
            } 
          />
          <Route path="*" element={<div className="flex h-screen w-screen items-center justify-center bg-charcoal-900 text-gray-100">Not Found</div>} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
