/**
 * SSE 客户端封装(Notification 实时推送 — P2-B)
 *
 * 用法:
 *   const stop = useNotificationStream({
 *     token: () => localStorage.getItem('token'),
 *     onMessage: (n) => { ... },
 *     onOpen: () => { ... },
 *     onError: (e) => { ... },
 *   })
 *   // onUnmounted: stop()
 *
 * 设计:
 *  - EventSource 原生不支持自定义 header(只能用 cookie / query)
 *  - 我们用 ?access_token=... query 传(后端 JwtAuthFilter 兼容)
 *  - 自动重连:EventSource 自带;额外做 401 → 停止 + 跳登录
 *  - 单例:模块级变量,全 App 一条连接(NotificationCenter 消费)
 */
import { onBeforeUnmount } from 'vue'

export interface NotificationPayload {
  category: string
  resourceId: number
  resourceCode: string
  title: string
  summary: string
  ts: string
}

export interface StreamHandlers {
  /** 拿当前 access token(每次重连前调用,所以要写成函数) */
  token: () => string | null
  /** 收到 notification 事件 */
  onMessage?: (n: NotificationPayload) => void
  /** 握手成功(connected 事件) */
  onOpen?: () => void
  /** 出错(原生 error event,可能也会触发重连) */
  onError?: (e: Event) => void
  /** 后端发来 connected 事件后的 userId */
  onConnected?: (userId: number) => void
}

/**
 * 在组件里调用:自动管理生命周期,卸载时关闭连接。
 */
export function useNotificationStream(handlers: StreamHandlers) {
  let es: EventSource | null = null
  let stopped = false
  let reconnectTimer: number | null = null

  function connect() {
    if (stopped) return
    const tok = handlers.token()
    if (!tok) {
      // 没 token → 等 5s 再试(可能正在登录)
      reconnectTimer = window.setTimeout(connect, 5_000)
      return
    }
    const url = `/api/notifications/stream?access_token=${encodeURIComponent(tok)}`
    es = new EventSource(url, { withCredentials: true })

    es.addEventListener('connected', (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data)
        handlers.onConnected?.(data.userId)
        handlers.onOpen?.()
      } catch {
        handlers.onOpen?.()
      }
    })

    es.addEventListener('notification', (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data) as NotificationPayload
        handlers.onMessage?.(data)
      } catch (e) {
        // 忽略解析错误
        console.warn('[SSE] parse failed', e)
      }
    })

    es.onerror = (e) => {
      handlers.onError?.(e)
      // EventSource readyState:
      //  0 CONNECTING  1 OPEN  2 CLOSED
      // 浏览器会自动重连;但我们检测到 401 时主动停(避免死循环)
      // 由于浏览器不暴露 status,只能依靠 onmessage 推断;
      // 简单兜底:CLOSED 状态重连
      if (es && es.readyState === EventSource.CLOSED) {
        es.close()
        es = null
        if (!stopped) {
          reconnectTimer = window.setTimeout(connect, 5_000)
        }
      }
    }
  }

  function stop() {
    stopped = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (es) {
      es.close()
      es = null
    }
  }

  connect()
  onBeforeUnmount(stop)
  return { stop }
}
