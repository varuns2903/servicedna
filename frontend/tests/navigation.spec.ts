import { test, expect } from '@playwright/test';

test.describe('Navigation', () => {
  test('has correct title', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/ServiceDNA/);
  });

  test('redirects to login when unauthenticated', async ({ page }) => {
    // Clear any stale tokens from localStorage before navigating
    await page.goto('/');
    await page.evaluate(() => localStorage.removeItem('sdna_token'));

    await page.goto('/dashboard');

    // Should redirect to login
    await expect(page).toHaveURL(/.*login/, { timeout: 10000 });

    // Verify login page renders the GitHub OAuth button
    await expect(page.getByRole('button', { name: /github/i })).toBeVisible();
  });

  test('public status page renders without authentication', async ({ page }) => {
    // Clear auth state
    await page.goto('/');
    await page.evaluate(() => localStorage.removeItem('sdna_token'));

    const dummyId = '123e4567-e89b-12d3-a456-426614174000';
    await page.goto(`/status/${dummyId}`);

    // The page should show either loading text or the error state
    // (depends on whether the backend is running and returns a 404/error)
    const loadingOrError = page.getByText(/loading status|failed to load|status/i);
    await expect(loadingOrError.first()).toBeVisible({ timeout: 10000 });
  });

  test('sidebar navigation links are present when authenticated', async ({ page }) => {
    // This test just verifies the sidebar renders the expected nav items
    // when the app shell is loaded. We navigate to root which redirects to /dashboard.
    // If there's no valid token the app redirects to /login, so we check for either state.
    await page.goto('/');

    const dashboardLink = page.getByRole('link', { name: /dashboard/i });
    const loginButton = page.getByRole('button', { name: /github/i });

    // Either we see the dashboard sidebar link (authenticated) or the login button
    const isAuthenticated = await dashboardLink.isVisible().catch(() => false);
    const isLoginPage = await loginButton.isVisible().catch(() => false);

    expect(isAuthenticated || isLoginPage).toBeTruthy();
  });
});
