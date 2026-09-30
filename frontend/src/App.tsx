import { useEffect, useState } from 'react'
import { getLiveness, getReadiness } from './shared/api/health'
import './App.css'

type ServiceState =
  | 'checking'
  | 'ready'
  | 'database-unavailable'
  | 'backend-unavailable'

const stateLabels: Record<ServiceState, string> = {
  checking: '正在检查服务状态',
  ready: '服务已就绪',
  'database-unavailable': '后端在线，数据库尚未就绪',
  'backend-unavailable': '无法连接后端',
}

function App() {
  const [serviceState, setServiceState] = useState<ServiceState>('checking')

  useEffect(() => {
    let active = true

    async function checkHealth() {
      try {
        const liveness = await getLiveness()
        if (liveness.status !== 'ok') {
          if (active) setServiceState('backend-unavailable')
          return
        }
      } catch {
        if (active) setServiceState('backend-unavailable')
        return
      }

      try {
        const readiness = await getReadiness()
        if (active) {
          setServiceState(
            readiness.status === 'ok' ? 'ready' : 'database-unavailable',
          )
        }
      } catch {
        if (active) setServiceState('database-unavailable')
      }
    }

    void checkHealth()
    return () => {
      active = false
    }
  }, [])

  return (
    <main className="shell">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">JobTrace Java Migration</p>
        <h1 id="page-title">职迹正在迁往新的 Java 后端</h1>
        <p className="lede">
          React 与 TypeScript 继续负责浏览器体验，Spring Boot 将逐步接管 API、业务规则和后台任务。
        </p>
        <div
          className={`status status-${serviceState}`}
          role="status"
          aria-live="polite"
        >
          <span aria-hidden="true" />
          {stateLabels[serviceState]}
        </div>
      </section>
    </main>
  )
}

export default App
