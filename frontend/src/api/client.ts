import axios from 'axios';

// The backend typically runs on 8080 during local dev
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('sdna_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

function clearSessionAndRedirect() {
  localStorage.removeItem('sdna_token');
  localStorage.removeItem('sdna_refresh_token');
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

// Shared across concurrent failed requests so a burst of expired-token errors triggers exactly
// one refresh call, not one per request.
let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem('sdna_refresh_token');
  if (!refreshToken) return null;

  if (!refreshPromise) {
    refreshPromise = axios
      .post(`${apiClient.defaults.baseURL}/auth/refresh`, { refreshToken })
      .then(({ data }) => {
        localStorage.setItem('sdna_token', data.token);
        localStorage.setItem('sdna_refresh_token', data.refreshToken);
        return data.token as string;
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { response, config } = error;
    if (!response || !config) {
      return Promise.reject(error);
    }

    // A JWT that's missing/expired/malformed is rejected by Spring Security's own filter chain
    // before any of our controllers run, so it comes back as a plain 403 with no JSON body — an
    // ApiException-driven 403 (a real authorization decision, e.g. ACCESS_DENIED) always carries
    // an errorCode. That distinction is what tells "your session expired" apart from "you're not
    // allowed to do this", which must never trigger a refresh-and-retry loop.
    const looksLikeExpiredToken = response.status === 403 && !response.data?.errorCode;

    if (looksLikeExpiredToken && !config._retried) {
      config._retried = true;
      const newToken = await refreshAccessToken();
      if (newToken) {
        config.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(config);
      }
      clearSessionAndRedirect();
    } else if (response.status === 401) {
      clearSessionAndRedirect();
    }

    return Promise.reject(error);
  }
);
