import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Activity, CheckCircle2 } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { useForgotPassword } from '@/hooks/useAuth';

export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const forgotPassword = useForgotPassword();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    forgotPassword.mutate(email);
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-charcoal-900 p-4 text-gray-100">
      <div className="w-full max-w-sm rounded-lg border border-charcoal-700 bg-charcoal-800 p-8 shadow-2xl">
        <div className="mb-8 flex flex-col items-center justify-center space-y-4 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-charcoal-700">
            <Activity className="h-6 w-6 text-emerald-400" />
          </div>
          <div>
            <h1 className="text-2xl font-medium tracking-tight text-white">Reset Password</h1>
            <p className="text-sm text-gray-400">We'll email you a link to reset it.</p>
          </div>
        </div>

        {forgotPassword.isSuccess ? (
          <div className="text-center">
            <CheckCircle2 className="mx-auto mb-3 h-10 w-10 text-emerald-400" />
            <p className="text-sm text-gray-300">
              If an account exists for <span className="text-white">{email}</span>, a reset link is
              on its way.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="mb-1 block text-sm text-gray-400">Email</label>
              <Input
                type="email"
                autoFocus
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
              />
            </div>
            <Button type="submit" className="w-full" disabled={forgotPassword.isPending}>
              {forgotPassword.isPending ? 'Sending...' : 'Send Reset Link'}
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
