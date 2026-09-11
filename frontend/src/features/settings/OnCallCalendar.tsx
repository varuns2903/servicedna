import { useMemo, useState } from 'react';
import {
  addMonths,
  differenceInCalendarDays,
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameMonth,
  isToday,
  parseISO,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import type { OnCallRotationDto, OnCallMemberDto } from '@/api/oncall.api';

interface OnCallCalendarProps {
  rotation: OnCallRotationDto;
}

const MEMBER_COLORS = [
  { dot: 'bg-emerald-400', chip: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20' },
  { dot: 'bg-blue-400', chip: 'bg-blue-500/10 text-blue-300 border-blue-500/20' },
  { dot: 'bg-amber-400', chip: 'bg-amber-500/10 text-amber-300 border-amber-500/20' },
  { dot: 'bg-fuchsia-400', chip: 'bg-fuchsia-500/10 text-fuchsia-300 border-fuchsia-500/20' },
  { dot: 'bg-cyan-400', chip: 'bg-cyan-500/10 text-cyan-300 border-cyan-500/20' },
  { dot: 'bg-orange-400', chip: 'bg-orange-500/10 text-orange-300 border-orange-500/20' },
];

function memberForDate(rotation: OnCallRotationDto, date: Date): OnCallMemberDto | null {
  if (rotation.members.length === 0) return null;
  const daysSince = differenceInCalendarDays(date, parseISO(rotation.startDate));
  if (daysSince < 0) return null;
  const periodIndex = Math.floor(daysSince / rotation.rotationLengthDays);
  const memberIndex = periodIndex % rotation.members.length;
  return rotation.members[memberIndex];
}

export function OnCallCalendar({ rotation }: OnCallCalendarProps) {
  const [monthCursor, setMonthCursor] = useState(() => new Date());

  const days = useMemo(() => {
    const start = startOfWeek(startOfMonth(monthCursor));
    const end = endOfWeek(endOfMonth(monthCursor));
    return eachDayOfInterval({ start, end });
  }, [monthCursor]);

  const colorByMemberId = useMemo(() => {
    const map = new Map<string, (typeof MEMBER_COLORS)[number]>();
    rotation.members.forEach((m, i) => map.set(m.organizationMemberId, MEMBER_COLORS[i % MEMBER_COLORS.length]));
    return map;
  }, [rotation.members]);

  if (rotation.members.length === 0) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Schedule</CardTitle>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setMonthCursor((d) => subMonths(d, 1))}
              className="rounded p-1 text-gray-400 hover:text-white"
              title="Previous month"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span className="min-w-[7rem] text-center text-sm font-medium text-gray-200">
              {format(monthCursor, 'MMMM yyyy')}
            </span>
            <button
              type="button"
              onClick={() => setMonthCursor((d) => addMonths(d, 1))}
              className="rounded p-1 text-gray-400 hover:text-white"
              title="Next month"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-7 gap-1 text-center text-xs text-gray-500 mb-1">
          {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((d) => (
            <div key={d} className="py-1">
              {d}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {days.map((day) => {
            const member = memberForDate(rotation, day);
            const color = member ? colorByMemberId.get(member.organizationMemberId) : undefined;
            const inMonth = isSameMonth(day, monthCursor);
            return (
              <div
                key={day.toISOString()}
                className={`flex min-h-[3.5rem] flex-col items-center rounded-md border p-1 text-xs ${
                  inMonth ? 'border-charcoal-700 bg-charcoal-900' : 'border-transparent opacity-30'
                } ${isToday(day) ? 'ring-1 ring-emerald-500' : ''}`}
              >
                <span className="text-gray-400">{format(day, 'd')}</span>
                {member && color && (
                  <div
                    className={`mt-1 flex w-full items-center justify-center gap-1 rounded px-1 py-0.5 ${color.chip}`}
                    title={member.email}
                  >
                    <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${color.dot}`} />
                    <span className="truncate">{member.email.split('@')[0]}</span>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <div className="mt-4 flex flex-wrap gap-3 border-t border-charcoal-700 pt-3">
          {rotation.members.map((m) => {
            const color = colorByMemberId.get(m.organizationMemberId)!;
            return (
              <div key={m.organizationMemberId} className="flex items-center gap-1.5 text-xs text-gray-400">
                <span className={`h-2 w-2 rounded-full ${color.dot}`} />
                {m.email}
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
