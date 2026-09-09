import { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { Activity } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { useLogin, useRegister } from '@/hooks/useAuth';

export function Login() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const login = useLogin();
  const register = useRegister();
  const mutation = mode === 'login' ? login : register;

  const handleGithubLogin = () => {
    // Redirect to backend OAuth2 endpoint
    window.location.href = import.meta.env.VITE_AUTH_LOGIN_URL || 'http://localhost:8080/oauth2/authorization/github';
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    mutation.mutate(
      { email, password },
      { onSuccess: () => navigate('/dashboard', { replace: true }) }
    );
  };

  const toggleMode = () => {
    setMode(mode === 'login' ? 'register' : 'login');
    login.reset();
    register.reset();
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-charcoal-900 p-4 text-gray-100">
      <div className="w-full max-w-sm rounded-lg border border-charcoal-700 bg-charcoal-800 p-8 shadow-2xl">
        <div className="mb-8 flex flex-col items-center justify-center space-y-4 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-charcoal-700">
            <Activity className="h-6 w-6 text-emerald-400" />
          </div>
          <div>
            <h1 className="text-2xl font-medium tracking-tight text-white">ServiceDNA</h1>
            <p className="text-sm text-gray-400">Monitor your systems.</p>
          </div>
        </div>

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
          <div>
            <label className="mb-1 block text-sm text-gray-400">Password</label>
            <Input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              minLength={8}
              required
            />
          </div>

          {mutation.isError && (
            <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
              {(axios.isAxiosError(mutation.error) && mutation.error.response?.data?.message) ||
                'Something went wrong. Please try again.'}
            </div>
          )}

          <Button type="submit" className="w-full" disabled={mutation.isPending}>
            {mutation.isPending
              ? mode === 'login'
                ? 'Signing in...'
                : 'Creating account...'
              : mode === 'login'
                ? 'Sign In'
                : 'Create Account'}
          </Button>
        </form>

        <button
          onClick={toggleMode}
          className="mt-3 w-full text-center text-xs text-gray-400 transition-colors hover:text-gray-200"
        >
          {mode === 'login' ? "Don't have an account? Sign up" : 'Already have an account? Sign in'}
        </button>

        <div className="my-6 flex items-center space-x-3">
          <div className="h-px flex-1 bg-charcoal-700" />
          <span className="text-xs text-gray-500">OR</span>
          <div className="h-px flex-1 bg-charcoal-700" />
        </div>

        <button
          onClick={handleGithubLogin}
          className="flex w-full items-center justify-center space-x-2 rounded-md bg-white px-4 py-2.5 text-sm font-medium text-charcoal-900 transition-colors hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-gray-400 focus:ring-offset-2 focus:ring-offset-charcoal-900"
        >
          <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path fillRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clipRule="evenodd" />
          </svg>
          <span>Continue with GitHub</span>
        </button>
      </div>
    </div>
  );
}
