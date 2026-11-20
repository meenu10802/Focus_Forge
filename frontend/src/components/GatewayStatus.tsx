import { useQuery } from '@tanstack/react-query'
import { api, API_URL } from '../api/client'

/** Dev aid: shows whether the API gateway is reachable from the browser (exercises CORS). */
export function GatewayStatus() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['gateway-health'],
    queryFn: async () => (await api.get<{ status: string }>('/api/health')).data,
    refetchInterval: 10_000,
    retry: false,
  })

  const up = data?.status === 'UP'
  const label = isLoading ? 'checking…' : up ? 'online' : isError ? 'unreachable' : (data?.status ?? 'unknown')
  const dot = isLoading ? 'bg-slate-500' : up ? 'bg-hp' : 'bg-hp-low'

  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-panel-edge px-3 py-1 text-xs text-muted">
      <span className={`size-2 rounded-full ${dot}`} />
      Gateway {API_URL.replace(/^https?:\/\//, '')} — {label}
    </span>
  )
}
