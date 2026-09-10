import { useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, XCircle, Loader2, Activity } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useConfirmEmailChange } from '@/hooks/useAuth';

export function ConfirmEmailChange() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const confirmEmailChange = useConfirmEmailChange();
  const attempted = useRef(false);

  useEffect(() => {
    if (token && !attempted.current) {
      attempted.current = true;
      confirmEmailChange.mutate(token);
    }
  }, [token, confirmEmailChange]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-charcoal-900 p-4 text-gray-100">
      <div className="w-full max-w-sm rounded-lg border border-charcoal-700 bg-charcoal-800 p-8 text-center shadow-2xl">
        <div className="mb-6 flex flex-col items-center justify-center space-y-4">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-charcoal-700">
            <Activity className="h-6 w-6 text-emerald-400" />
          </div>
          <h1 className="text-2xl font-medium tracking-tight text-white">ServiceDNA</h1>
        </div>

        {!token ? (
          <>
            <XCircle className="mx-auto mb-3 h-10 w-10 text-rose-500" />
            <p className="text-sm text-gray-300">This confirmation link is missing a token.</p>
          </>
        ) : confirmEmailChange.isPending ? (
          <>
            <Loader2 className="mx-auto mb-3 h-10 w-10 animate-spin text-emerald-400" />
            <p className="text-sm text-gray-300">Confirming your new email...</p>
          </>
        ) : confirmEmailChange.isSuccess ? (
          <>
            <CheckCircle2 className="mx-auto mb-3 h-10 w-10 text-emerald-400" />
            <p className="text-sm text-gray-300">
              Your email has been updated. Sign in with your new email address.
            </p>
          </>
        ) : (
          <>
            <XCircle className="mx-auto mb-3 h-10 w-10 text-rose-500" />
            <p className="text-sm text-gray-300">
              This confirmation link is invalid or has expired.
            </p>
          </>
        )}

        <Link to="/login" className="mt-6 block">
          <Button className="w-full">Go to Sign In</Button>
        </Link>
      </div>
    </div>
  );
}
