import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FINISHED, TestingApi, type StartSuite, type TestCollection, type TestRequest } from '@/api/testing.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

function useOrgId() {
  return useOrganizationStore((s) => s.selectedOrganizationId);
}

export function useTestRun(runId: string | null) {
  const orgId = useOrgId();
  return useQuery({
    queryKey: ['organizations', orgId, 'test-runs', runId],
    queryFn: () => TestingApi.getRun(orgId!, runId!),
    enabled: !!orgId && !!runId,
    // Poll until the run finishes, then a little longer: late spans keep filling in hops.
    refetchInterval: (query) => {
      const run = query.state.data;
      if (!run || !FINISHED.includes(run.status)) return 1000;
      const age = run.finishedAt ? Date.now() - new Date(run.finishedAt).getTime() : 0;
      return age < 15_000 ? 2000 : false;
    },
  });
}

export function useRecentTestRuns() {
  const orgId = useOrgId();
  return useQuery({
    queryKey: ['organizations', orgId, 'test-runs'],
    queryFn: () => TestingApi.runs(orgId!, 20),
    enabled: !!orgId,
  });
}

export function useSendTestRun() {
  const orgId = useOrgId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: TestRequest) => TestingApi.run(orgId!, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'test-runs'] }),
  });
}

export function useReplay() {
  const orgId = useOrgId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { traceId: string; spanId?: string; environment?: string | null }) => TestingApi.replay(orgId!, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'test-runs'] }),
  });
}

export function useTestCollections() {
  const orgId = useOrgId();
  return useQuery({
    queryKey: ['organizations', orgId, 'test-collections'],
    queryFn: () => TestingApi.collections(orgId!),
    enabled: !!orgId,
  });
}

export function useSaveTestCollection() {
  const orgId = useOrgId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }: Omit<TestCollection, 'id' | 'updatedAt'> & { id?: string }) =>
      id ? TestingApi.updateCollection(orgId!, id, body) : TestingApi.createCollection(orgId!, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'test-collections'] }),
  });
}

export function useDeleteTestCollection() {
  const orgId = useOrgId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => TestingApi.deleteCollection(orgId!, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'test-collections'] }),
  });
}

export function useStartTestSuite() {
  const orgId = useOrgId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: StartSuite) => TestingApi.startSuite(orgId!, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'test-suites'] }),
  });
}

export function useTestSuites() {
  const orgId = useOrgId();
  return useQuery({
    queryKey: ['organizations', orgId, 'test-suites'],
    queryFn: () => TestingApi.suites(orgId!),
    enabled: !!orgId,
    refetchInterval: (query) => (query.state.data?.some((s) => s.status === 'RUNNING') ? 2000 : false),
  });
}

export function useTestSuite(suiteId: string | null) {
  const orgId = useOrgId();
  return useQuery({
    queryKey: ['organizations', orgId, 'test-suites', suiteId],
    queryFn: () => TestingApi.getSuite(orgId!, suiteId!),
    enabled: !!orgId && !!suiteId,
    refetchInterval: (query) => (query.state.data?.status === 'RUNNING' ? 2000 : false),
  });
}

export function useEnvironments(orgId?: string | null) {
  const selected = useOrgId();
  const id = orgId ?? selected;
  return useQuery({
    queryKey: ['organizations', id, 'environments'],
    queryFn: () => TestingApi.environments(id!),
    enabled: !!id,
  });
}

export function useUpdateEnvironment(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ environment, allowTestRuns }: { environment: string; allowTestRuns: boolean }) =>
      TestingApi.updateEnvironment(orgId, environment, allowTestRuns),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'environments'] }),
  });
}

export function useServiceOperations(serviceId: string | null) {
  const orgId = useOrgId();
  return useQuery({
    queryKey: ['organizations', orgId, 'services', serviceId, 'operations'],
    queryFn: () => TestingApi.operations(orgId!, serviceId!),
    enabled: !!orgId && !!serviceId,
  });
}
