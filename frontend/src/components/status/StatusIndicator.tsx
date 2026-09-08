import * as React from 'react';
import { CheckCircle2, AlertTriangle, XCircle, Clock, HelpCircle } from 'lucide-react';
import { cn } from '@/utils/cn';

export type ServiceStatus = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'RECOVERING' | 'UNKNOWN';

interface StatusIndicatorProps extends React.HTMLAttributes<HTMLDivElement> {
  status: ServiceStatus;
  showLabel?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export function StatusIndicator({ status, showLabel = true, size = 'md', className, ...props }: StatusIndicatorProps) {
  const config = {
    HEALTHY: {
      color: 'text-emerald-400',
      icon: CheckCircle2,
      label: 'Healthy',
    },
    DEGRADED: {
      color: 'text-amber-400',
      icon: AlertTriangle,
      label: 'Degraded',
    },
    DOWN: {
      color: 'text-rose-500',
      icon: XCircle,
      label: 'Down',
    },
    RECOVERING: {
      color: 'text-blue-400',
      icon: Clock,
      label: 'Recovering',
    },
    UNKNOWN: {
      color: 'text-gray-500',
      icon: HelpCircle,
      label: 'Unknown',
    },
  };

  const { color, icon: Icon, label } = config[status] || config.UNKNOWN;
  
  const sizeClasses = {
    sm: 'h-3.5 w-3.5',
    md: 'h-5 w-5',
    lg: 'h-6 w-6',
  };

  return (
    <div className={cn('flex items-center space-x-2', className)} {...props}>
      <Icon className={cn(color, sizeClasses[size])} strokeWidth={2.5} />
      {showLabel && <span className="font-medium text-gray-200 text-sm">{label}</span>}
    </div>
  );
}
