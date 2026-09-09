import { useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Activity, CheckCircle2 } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { useResetPassword } from '@/hooks/useAuth';

export function ResetPassword() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');
  const [newPassword, setNewPassword] = useState('');
  const resetPassword = useResetPassword();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!token) return;
    resetPassword.mutate({ token, newPassword });
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-charcoal-900 p-4 text-gray-100">
      <div className="w-full max-w-sm rounded-lg border border-charcoal-700 bg-charcoal-800 p-8 shadow-2xl">
        <div className="mb-8 flex flex-col items-center justify-center space-y-4 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-charcoal-700">
            <Activity className="h-6 w-6 text-emerald-400" />
          </div>
          <div>
            <h1 className="text-2xl font-medium tracking-tight text-white">Set New Password</h1>
          </div>
        </div>

        {!token ? (
          <p className="text-center text-sm text-rose-400">This reset link is missing a token.</p>
        ) : resetPassword.isSuccess ? (
          <div className="text-center">
            <CheckCircle2 className="mx-auto mb-3 h-10 w-10 text-emerald-400" />
            <p className="mb-4 text-sm text-gray-300">Your password has been reset.</p>
            <Button className="w-full" onClick={() => navigate('/login', { replace: true })}>
              Go to Sign In
            </Button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="mb-1 block text-sm text-gray-400">New Password</label>
              <Input
                type="password"
                autoFocus
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="••••••••"
                minLength={8}
                required
              />
            </div>

            {resetPassword.isError && (
              <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
                {(axios.isAxiosError(resetPassword.error) &&
                  resetPassword.error.response?.data?.message) ||
                  'Something went wrong. Please try again.'}
              </div>
            )}

            <Button type="submit" className="w-full" disabled={resetPassword.isPending}>
              {resetPassword.isPending ? 'Resetting...' : 'Reset Password'}
            </Button>
          </form>
        )}

        <Link
          to="/login"
          className="mt-4 block text-center text-xs text-gray-400 transition-colors hover:text-gray-200"
        >
          Back to sign in
        </Link>
      </div>
    </div>
  );
}
