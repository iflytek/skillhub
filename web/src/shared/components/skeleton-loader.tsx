export function SkeletonCard() {
  return (
    <div className="rounded-xl border bg-card p-5" style={{ borderColor: 'hsl(var(--border-card))' }}>
      <div className="h-5 animate-shimmer rounded-lg w-3/4 mb-4"></div>
      <div className="h-3 animate-shimmer rounded-md w-full mb-2.5"></div>
      <div className="h-3 animate-shimmer rounded-md w-5/6 mb-5"></div>
      <div className="flex gap-3 mt-4">
        <div className="h-6 animate-shimmer rounded-full w-16"></div>
        <div className="h-6 animate-shimmer rounded-full w-20"></div>
        <div className="h-6 animate-shimmer rounded-full w-14"></div>
      </div>
    </div>
  )
}

interface SkeletonListProps {
  count?: number
}

export function SkeletonList({ count = 6 }: SkeletonListProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  )
}
