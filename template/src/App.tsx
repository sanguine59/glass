import { useState } from 'react'

export default function App() {
  const [count, setCount] = useState(0)

  return (
    <main>
      <h1>glass</h1>
      <p>
        Vite dev server is running on-device. Edit <code>src/App.tsx</code> and
        this should hot-reload without a manual refresh.
      </p>
      <button onClick={() => setCount((c) => c + 1)}>count is {count}</button>
      <p className="hint">
        The counter surviving an edit is the proof that HMR works end to end:
        module replacement preserves React state, a full page reload does not.
      </p>
    </main>
  )
}
