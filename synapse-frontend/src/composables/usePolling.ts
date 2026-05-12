import { ref, onUnmounted } from 'vue'

export function usePolling<T>(
  fn: () => Promise<T>,
  interval: number = 2000,
  maxAttempts: number = 30
) {
  const isPolling = ref(false)
  const attempts = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  function start(condition?: (result: T) => boolean) {
    if (timer) return
    isPolling.value = true
    attempts.value = 0

    timer = setInterval(async () => {
      attempts.value++
      if (attempts.value >= maxAttempts) {
        stop()
        return
      }

      try {
        const result = await fn()
        if (condition?.(result)) {
          stop()
        }
      } catch {
        // ignore polling errors
      }
    }, interval)
  }

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    isPolling.value = false
  }

  onUnmounted(stop)

  return {
    isPolling,
    attempts,
    start,
    stop
  }
}
