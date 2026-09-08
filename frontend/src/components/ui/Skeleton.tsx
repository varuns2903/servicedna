import { cn } from '@/utils/cn';

export function Skeleton({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('animate-pulse rounded-md bg-charcoal-700/50', className)}
      {...props}
    />
  );
}
