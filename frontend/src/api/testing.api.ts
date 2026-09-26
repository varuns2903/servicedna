import { apiClient } from './client';
import type { Protocol } from './graph.api';

export type TestProtocol = Extract<Protocol, 'HTTP' | 'GRPC' | 'GRAPHQL' | 'MESSAGING'>;
export type TestRunStatus = 'QUEUED' | 'RUNNING' | 'WAITING' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT';

export const FINISHED: TestRunStatus[] = ['COMPLETED', 'FAILED', 'TIMED_OUT'];

export interface TestRequest {
  environment?: string | null;
  protocol: TestProtocol;
  serviceId?: string | null;
  serviceName?: string | null;
  method?: string | null;
  path?: string | null;
  grpcMethod?: string | null;
  topic?: string | null;
  key?: string | null;
  headers?: Record<string, string>;
  body?: string | null;
  testMode?: boolean;
}

export interface Hop {
  spanId: string;
  parentSpanId: string | null;
  service: string;
  operation: string;
  kind: string;
  start: string;
  durationMs: number;
  error: boolean;
  statusMessage: string | null;
  httpStatus: number | null;
  requestBody: string | null;
  responseBody: string | null;
  captured: Record<string, string> | null;
}

/** What the runner saw when it sent the request. */
export interface EntryResult {
  sent: boolean;
  error: string | null;
  status: number | null;
  headers: Record<string, string> | null;
  body: string | null;
  durationMs: number | null;
  partition: number | null;
  offset: number | null;
}

export interface AssertionResult {
  description: string;
  passed: boolean;
  message: string | null;
}

export interface TestRun {
  id: string;
  environment: string | null;
  protocol: TestProtocol;
  serviceId: string | null;
  serviceName: string | null;
  target: Record<string, unknown>;
  request: Record<string, unknown>;
  traceId: string;
  status: TestRunStatus;
  result: EntryResult | null;
  error: string | null;
  runner: string | null;
  createdAt: string;
  finishedAt: string | null;
  caseName: string | null;
  passed: boolean | null;
  assertionResults: AssertionResult[] | null;
  hops: Hop[] | null;
}

/** See AssertionEvaluator: a target plus any of exists, status, latencyMs, request, response, captured. */
export type Assertion = Record<string, unknown> & {
  target: { entry?: boolean; service?: string; operation?: string };
};

export interface TestCase {
  name: string;
  request: TestRequest;
  assertions: Assertion[] | null;
}

export interface TestCollection {
  id: string;
  name: string;
  description: string | null;
  cases: TestCase[];
  updatedAt: string;
}

export interface TestSuite {
  id: string;
  name: string;
  collectionId: string | null;
  environment: string | null;
  status: 'RUNNING' | 'PASSED' | 'FAILED';
  createdAt: string;
  finishedAt: string | null;
  passed: number;
  failed: number;
  pending: number;
  runs: TestRun[] | null;
}

/** Runs a saved collection, or cases given inline. */
export interface StartSuite {
  name?: string;
  collectionId?: string;
  environment?: string | null;
  cases?: TestCase[];
}

export interface EnvironmentSetting {
  environment: string;
  productionLike: boolean;
  allowTestRuns: boolean;
}

export interface CatalogOperation {
  protocol: Protocol;
  name: string;
  source: string;
  description: string | null;
  requestSchema: string | null;
  observed: boolean;
  callsLast24h: number;
}

export const TestingApi = {
  run: async (orgId: string, request: TestRequest) =>
    (await apiClient.post<TestRun>(`/organizations/${orgId}/test-runs`, request)).data,
  runs: async (orgId: string, limit = 50) =>
    (await apiClient.get<TestRun[]>(`/organizations/${orgId}/test-runs`, { params: { limit } })).data,
  getRun: async (orgId: string, runId: string) =>
    (await apiClient.get<TestRun>(`/organizations/${orgId}/test-runs/${runId}`)).data,

  collections: async (orgId: string) =>
    (await apiClient.get<TestCollection[]>(`/organizations/${orgId}/test-collections`)).data,
  createCollection: async (orgId: string, body: Omit<TestCollection, 'id' | 'updatedAt'>) =>
    (await apiClient.post<TestCollection>(`/organizations/${orgId}/test-collections`, body)).data,
  updateCollection: async (orgId: string, id: string, body: Omit<TestCollection, 'id' | 'updatedAt'>) =>
    (await apiClient.put<TestCollection>(`/organizations/${orgId}/test-collections/${id}`, body)).data,
  deleteCollection: async (orgId: string, id: string) => {
    await apiClient.delete(`/organizations/${orgId}/test-collections/${id}`);
  },

  startSuite: async (orgId: string, body: StartSuite) =>
    (await apiClient.post<TestSuite>(`/organizations/${orgId}/test-suites`, body)).data,
  suites: async (orgId: string) => (await apiClient.get<TestSuite[]>(`/organizations/${orgId}/test-suites`)).data,
  getSuite: async (orgId: string, id: string) =>
    (await apiClient.get<TestSuite>(`/organizations/${orgId}/test-suites/${id}`)).data,

  environments: async (orgId: string) =>
    (await apiClient.get<EnvironmentSetting[]>(`/organizations/${orgId}/environments`)).data,
  updateEnvironment: async (orgId: string, environment: string, allowTestRuns: boolean) =>
    (await apiClient.put<EnvironmentSetting>(`/organizations/${orgId}/environments/${encodeURIComponent(environment)}`, { allowTestRuns })).data,

  operations: async (orgId: string, serviceId: string) =>
    (await apiClient.get<CatalogOperation[]>(`/organizations/${orgId}/services/${serviceId}/operations`)).data,
};
