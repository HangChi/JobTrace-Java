import './App.css'

function App() {
  return (
    <main className="shell">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">JobTrace Java Migration</p>
        <h1 id="page-title">职迹正在迁往新的 Java 后端</h1>
        <p className="lede">
          React 与 TypeScript 继续负责浏览器体验，Spring Boot 将逐步接管 API、业务规则和后台任务。
        </p>
        <div className="status" role="status">
          <span aria-hidden="true" />
          工程骨架已就绪
        </div>
      </section>
    </main>
  )
}

export default App

