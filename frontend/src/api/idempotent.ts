import axios from 'axios'
import { api } from './client'

/**
 * POST with an Idempotency-Key that survives retries and page reloads.
 *
 * One key per logical operation (`operationId`, e.g. "complete:<taskId>"), kept
 * in sessionStorage until the operation definitively succeeds or fails. If the
 * response is lost (timeout, dropped connection, reload mid-request) the retry
 * re-sends the SAME key and the server replays its original answer instead of
 * performing the action twice.
 */
const storageKey = (operationId: string) => `ff.idem.${operationId}`

function keyFor(operationId: string): string {
  try {
    const existing = sessionStorage.getItem(storageKey(operationId))
    if (existing) return existing
    const fresh = crypto.randomUUID()
    sessionStorage.setItem(storageKey(operationId), fresh)
    return fresh
  } catch {
    return crypto.randomUUID()
  }
}

function forget(operationId: string) {
  try {
    sessionStorage.removeItem(storageKey(operationId))
  } catch {
    /* storage unavailable */
  }
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/** Only retry what is safe to retry with the same key: no response, 5xx, or "still in flight". */
function isRetryable(err: unknown): boolean {
  if (!axios.isAxiosError(err)) return false
  if (!err.response) return true
  const { status, data } = err.response
  if (status >= 500) return true
  return status === 409 && typeof data?.detail === 'string' && data.detail.includes('still being processed')
}

export async function postIdempotent<T>(url: string, operationId: string): Promise<T> {
  const key = keyFor(operationId)
  let lastError: unknown
  for (const backoff of [0, 600, 1800]) {
    if (backoff) await sleep(backoff)
    try {
      const res = await api.post<T>(url, null, { headers: { 'Idempotency-Key': key } })
      forget(operationId)
      return res.data
    } catch (err) {
      lastError = err
      if (!isRetryable(err)) break
    }
  }
  forget(operationId) // definitive failure: the next click is a new logical request
  throw lastError
}
