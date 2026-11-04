import { useEffect, useRef, useState } from 'react'
import Board3D from './Board3D'
import './App.css'

type Mode = 'human' | 'vs-auto' | 'auto-batch'
type Strategy = string
type PlayerOption = { value: Strategy; label: string }
type Speed = 'normal' | 'fast' | 'final' | 'step'
type StartingOrder = 'a' | 'b' | 'random'

type GameState = {
  cells: number[][][]
  currentPlayer: number
  winner: number | null
  finished: boolean
  moveCount: number
}

type CreatedGame = { id: string; state: GameState }
type Move = { x: number; y: number; z: number; player: number }
type Match = { winner: number | null; moveCount: number; moves: Move[] }
type Statistics = {
  games: number
  aWins: number
  bWins: number
  draws: number
  averageMoves: number
  elapsedMillis: number
  seed: number
}

function emptyCells() {
  return Array.from({ length: 5 }, () =>
    Array.from({ length: 5 }, () => Array(5).fill(0)),
  )
}

function initialState(): GameState {
  return {
    cells: emptyCells(),
    currentPlayer: 1,
    winner: null,
    finished: false,
    moveCount: 0,
  }
}

function copyCells(cells: number[][][]) {
  return cells.map((layer) => layer.map((row) => [...row]))
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function nextFrame(): Promise<void> {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()))
}

function aStarts(order: StartingOrder, seed: number, index: number): boolean {
  if (order === 'a') return true
  if (order === 'b') return false

  // 描画あり／なしで同じ試合順になるようにする。
  let n = (seed | 0) ^ index
  n = Math.imul(n ^ (n >>> 16), 0x45d9f3b)
  n = Math.imul(n ^ (n >>> 16), 0x45d9f3b)
  n ^= n >>> 16
  return (n & 1) === 0
}

async function api<T>(path: string, options: RequestInit): Promise<T> {
  const response = await fetch(path, options)
  const data = await response.json()
  if (!response.ok) throw new Error(data.error || '通信に失敗しました')
  return data as T
}

export default function App() {
  const initialized = useRef(false)
  const runToken = useRef(0)
  const activeRequest = useRef<AbortController | null>(null)
  const stepResume = useRef<(() => void) | null>(null)

  const [gameId, setGameId] = useState<string | null>(null)
  const [game, setGame] = useState<GameState | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const [mode, setMode] = useState<Mode>('human')
  const [humanSide, setHumanSide] = useState<1 | 2>(1)
  const [startingOrder, setStartingOrder] = useState<StartingOrder>('random')
  const [displayedAFirst, setDisplayedAFirst] = useState(true)
  const [opponentPlayer, setOpponentPlayer] = useState<Strategy>('TACTICAL')
  const [playerA, setPlayerA] = useState<Strategy>('HEURISTIC')
  const [playerB, setPlayerB] = useState<Strategy>('TACTICAL')
  const [strategies, setStrategies] = useState<PlayerOption[]>([])
  const [gameCount, setGameCount] = useState(100)
  const [drawMatches, setDrawMatches] = useState(false)
  const [speed, setSpeed] = useState<Speed>('final')
  const [stepPrompt, setStepPrompt] = useState<'move' | 'match' | null>(null)
  const [running, setRunning] = useState(false)
  const [progress, setProgress] = useState(0)
  const [stats, setStats] = useState<Statistics | null>(null)

  useEffect(() => {
    if (initialized.current) return
    initialized.current = true
    api<CreatedGame>('/api/games', { method: 'POST' })
      .then(({ id, state }) => {
        setGameId(id)
        setGame(state)
      })
      .catch(() => setError('Javaサーバーに接続できません'))
  }, [])

  useEffect(() => {
    let active = true
    api<PlayerOption[]>('/api/players/strategies', { method: 'GET' })
      .then((options) => {
        if (!active) return
        setStrategies(options)
        const available = (id: string) => options.some((player) => player.value === id)
        setOpponentPlayer((id) => available(id) ? id : options[0]?.value ?? '')
        setPlayerA((id) => available(id) ? id : options[0]?.value ?? '')
        setPlayerB((id) => available(id) ? id : options[0]?.value ?? '')
      })
      .catch(() => { if (active) setError('プレイヤー一覧を取得できません') })
    return () => { active = false }
  }, [])

  async function drop(x: number, y: number) {
    if (!gameId || !game || game.finished || busy || mode === 'auto-batch') return
    if (mode === 'vs-auto' && game.currentPlayer !== humanSide) return

    setBusy(true)
    setError('')
    try {
      const state = await api<GameState>(`/api/games/${gameId}/moves`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ x, y }),
      })
      setGame(state)

      if (mode === 'vs-auto' && !state.finished && state.currentPlayer !== humanSide) {
        const next = await api<GameState>(`/api/games/${gameId}/auto-move`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ strategy: opponentPlayer }),
        })
        setGame(next)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作に失敗しました')
    } finally {
      setBusy(false)
    }
  }

  async function reset(nextMode: Mode = mode, nextHumanSide: 1 | 2 = humanSide) {
    if (!gameId || busy || running) return
    setBusy(true)
    try {
      let next = await api<GameState>(`/api/games/${gameId}/reset`, {
        method: 'POST',
      })
      if (nextMode === 'vs-auto' && nextHumanSide === 2) {
        next = await api<GameState>(`/api/games/${gameId}/auto-move`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ strategy: opponentPlayer }),
        })
      }
      setGame(next)
      setError('')
    } catch {
      setError('リセットに失敗しました')
    } finally {
      setBusy(false)
    }
  }

  async function changeHumanSide(next: 1 | 2) {
    if (busy || running || next === humanSide) return
    setHumanSide(next)
    await reset('vs-auto', next)
  }

  async function changeMode(next: Mode) {
    if (busy || running) return
    setMode(next)
    setStats(null)
    setProgress(0)
    setError('')

    if (next === 'auto-batch') {
      setDisplayedAFirst(startingOrder !== 'b')
      setGame(initialState())
    } else {
      await reset(next, humanSide)
    }
  }

  function stopBatch() {
    runToken.current++
    activeRequest.current?.abort()
    activeRequest.current = null
    const resume = stepResume.current
    stepResume.current = null
    setStepPrompt(null)
    resume?.()
    setRunning(false)
  }

  function waitForStep(token: number, prompt: 'move' | 'match'): Promise<boolean> {
    if (token !== runToken.current) return Promise.resolve(false)
    return new Promise((resolve) => {
      stepResume.current = () => resolve(token === runToken.current)
      setStepPrompt(prompt)
    })
  }

  function nextStep() {
    const resume = stepResume.current
    if (!resume) return
    stepResume.current = null
    setStepPrompt(null)
    resume()
  }

  async function showMatch(match: Match, token: number, playback: Speed) {
    const cells = emptyCells()

    if (playback === 'step') {
      setGame(initialState())
      await nextFrame()
    }

    if (playback === 'final') {
      for (const move of match.moves) {
        cells[move.z][move.y][move.x] = move.player
      }
      setGame({
        cells,
        currentPlayer: match.winner ?? 1,
        winner: match.winner,
        finished: true,
        moveCount: match.moveCount,
      })
      await nextFrame()
      return token === runToken.current
    }

    for (let i = 0; i < match.moves.length; i++) {
      if (token !== runToken.current) return false
      if (playback === 'step' && !await waitForStep(token, 'move')) return false
      if (token !== runToken.current) return false
      const move = match.moves[i]
      cells[move.z][move.y][move.x] = move.player
      const last = i === match.moves.length - 1

      setGame({
        cells: copyCells(cells),
        currentPlayer: last && match.winner ? match.winner : 3 - move.player,
        winner: last ? match.winner : null,
        finished: last,
        moveCount: i + 1,
      })
      if (playback !== 'step') await wait(playback === 'fast' ? 18 : 110)
    }

    if (token !== runToken.current) return false
    if (playback === 'step') {
      await nextFrame()
      return token === runToken.current
    }
    await wait(playback === 'fast' ? 80 : 450)
    return token === runToken.current
  }

  async function startBatch() {
    const count = Number(gameCount)
    if (running || busy) return
    if (!Number.isInteger(count) || count < 1 || count > 100000) {
      setError('試合数は1〜100000で指定してください')
      return
    }

    const token = ++runToken.current
    const controller = new AbortController()
    activeRequest.current = controller
    const seed = Date.now()
    setRunning(true)
    setProgress(0)
    setStats(null)
    setStepPrompt(null)
    setDisplayedAFirst(startingOrder !== 'b')
    setGame(initialState())
    setError('')

    try {
      if (!drawMatches) {
        const result = await api<Statistics>('/api/players/simulate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          signal: controller.signal,
          body: JSON.stringify({ a: playerA, b: playerB, games: count, seed, order: startingOrder }),
        })
        if (token === runToken.current) {
          setStats(result)
          setProgress(count)
        }
        return
      }

      let aWins = 0
      let bWins = 0
      let draws = 0
      let totalMoves = 0
      const started = performance.now()

      for (let i = 0; i < count; i++) {
        if (token !== runToken.current) break
        const aFirst = aStarts(startingOrder, seed, i)
        const match = await api<Match>('/api/players/match', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          signal: controller.signal,
          body: JSON.stringify({
            first: aFirst ? playerA : playerB,
            second: aFirst ? playerB : playerA,
            seed: seed + i,
          }),
        })

        if (token !== runToken.current) break
        setDisplayedAFirst(aFirst)
        const completed = await showMatch(match, token, speed)
        if (!completed) break

        if (match.winner === null) draws++
        else if ((match.winner === 1) === aFirst) aWins++
        else bWins++

        totalMoves += match.moveCount
        setProgress(i + 1)
        setStats({
          games: i + 1,
          aWins,
          bWins,
          draws,
          averageMoves: totalMoves / (i + 1),
          elapsedMillis: Math.round(performance.now() - started),
          seed,
        })

        if (speed === 'step' && i + 1 < count) {
          if (!await waitForStep(token, 'match')) break
        }
      }
    } catch (err) {
      if (token === runToken.current) {
        setError(err instanceof Error ? err.message : '対戦に失敗しました')
      }
    } finally {
      if (token === runToken.current) {
        activeRequest.current = null
        setRunning(false)
      }
    }
  }

  const cpuName = (id: Strategy) => {
    const label = strategies.find((option) => option.value === id)?.label ?? id
    return label.split(/[（(]/)[0].trim()
  }

  // 赤と青は先手・後手。連続対戦ではCPU1とCPU2が入れ替わることがある。
  const names = mode === 'human'
    ? ['ユーザー１', 'ユーザー２']
    : mode === 'vs-auto'
      ? humanSide === 1
        ? ['ユーザー', cpuName(opponentPlayer)]
        : [cpuName(opponentPlayer), 'ユーザー']
      : displayedAFirst
        ? [`CPU1（${cpuName(playerA)}）`, `CPU2（${cpuName(playerB)}）`]
        : [`CPU2（${cpuName(playerB)}）`, `CPU1（${cpuName(playerA)}）`]

  let status = '読み込み中'
  if (game) {
    status = game.finished
      ? (game.winner ? `${names[game.winner - 1]}の勝利` : '引き分け')
      : `${names[game.currentPlayer - 1]}の手番`
  }
  if (mode === 'auto-batch' && !drawMatches) {
    status = running ? '集計中...' : stats ? '対戦終了' : '対戦待ち'
  }

  const boardDisabled = !game || game.finished || busy || mode === 'auto-batch' ||
    (mode === 'vs-auto' && game.currentPlayer !== humanSide)

  return (
    <main className="page">

      <div className="layout">
        <section className="board-area">
          <div className="board">
            <Board3D
              cells={game?.cells ?? emptyCells()}
              winner={game?.winner ?? null}
              disabled={boardDisabled}
              onDrop={drop}
            />
          </div>
          <p className="hint">
            {mode === 'auto-batch'
              ? 'ドラッグで回転 / ホイールで拡大縮小'
              : '盤面か一番上の駒をクリック / ドラッグで回転'}
          </p>
        </section>

        <aside className="sidebar">
          <h1>コネクトフォー3D</h1>
          <p className="description">5×5×5の盤面で、先に4個並べたプレイヤーの勝ち。</p>

          <div className="mode-controls">
            <label>
              対戦モード
              <select
                value={mode}
                disabled={busy || running}
                onChange={(event) => void changeMode(event.target.value as Mode)}
              >
                <option value="human">人 vs 人</option>
                <option value="vs-auto">人 vs CPU</option>
                <option value="auto-batch">CPU vs CPU</option>
              </select>
            </label>

            {mode === 'vs-auto' && (
              <>
              <div className="turn-choice">
                <span>先手・後手</span>
                <div role="group" aria-label="人の先手・後手">
                  <button type="button" aria-pressed={humanSide === 1} disabled={busy || !gameId} onClick={() => void changeHumanSide(1)}>先手</button>
                  <button type="button" aria-pressed={humanSide === 2} disabled={busy || !gameId} onClick={() => void changeHumanSide(2)}>後手</button>
                </div>
              </div>
              <label>
                対戦するCPU
                <select value={opponentPlayer} disabled={busy} onChange={(e) => setOpponentPlayer(e.target.value as Strategy)}>
                  {strategies.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
              </label>
              </>
            )}

            {mode === 'auto-batch' && (
              <>
                <label>
                  CPU1
                  <select value={playerA} disabled={running} onChange={(e) => setPlayerA(e.target.value as Strategy)}>
                    {strategies.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                  </select>
                </label>
                <label>
                  CPU2
                  <select value={playerB} disabled={running} onChange={(e) => setPlayerB(e.target.value as Strategy)}>
                    {strategies.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                  </select>
                </label>
                <label>
                  先手
                  <select value={startingOrder} disabled={running} onChange={(e) => {
                    const order = e.target.value as StartingOrder
                    setStartingOrder(order)
                    setDisplayedAFirst(order !== 'b')
                  }}>
                    <option value="a">CPU1を先手に固定</option>
                    <option value="b">CPU2を先手に固定</option>
                    <option value="random">ランダム（試合ごと）</option>
                  </select>
                </label>
                <label>
                  試合数
                  <input
                    type="number"
                    min="1"
                    max="100000"
                    value={gameCount}
                    disabled={running}
                    onChange={(e) => setGameCount(Number(e.target.value))}
                  />
                </label>
                <label className="checkbox-control">
                  <input type="checkbox" checked={drawMatches} disabled={running} onChange={(e) => setDrawMatches(e.target.checked)} />
                  盤面を描画する
                </label>
                {drawMatches && (
                  <label>
                    表示速度
                    <select value={speed} disabled={running} onChange={(e) => setSpeed(e.target.value as Speed)}>
                      <option value="normal">通常（110ms / 手）</option>
                      <option value="fast">高速（18ms / 手）</option>
                      <option value="final">最速（1試合ずつ最終盤面を表示）</option>
                      <option value="step">一手ずつ（クリックで進む）</option>
                    </select>
                  </label>
                )}
                <div className="batch-actions">
                  <button type="button" onClick={running ? stopBatch : () => void startBatch()}>
                    {running ? '対戦を停止' : '対戦開始'}
                  </button>
                  {running && drawMatches && speed === 'step' && (
                    <button type="button" disabled={stepPrompt === null} onClick={nextStep}>
                      {stepPrompt === 'match' ? '次の試合' : '次の一手'}
                    </button>
                  )}
                </div>
                {running && <p className="batch-progress">{progress} / {gameCount} 試合終了</p>}
              </>
            )}
          </div>

          <div className="status">{status}</div>

          <div className="players">
            <span><i className="piece one" /> {names[0]}</span>
            <span><i className="piece two" /> {names[1]}</span>
          </div>

          <div className="moves">手数 <span>{game?.moveCount ?? 0} / 125</span></div>

          {mode !== 'auto-batch' && (
            <button type="button" onClick={() => void reset()} disabled={!gameId || busy}>
              もう一度
            </button>
          )}

          {mode === 'auto-batch' && stats && (
            <div className="batch-results">
              <h2>対戦結果</h2>
              <p>終了 {stats.games} 試合</p>
              <div><span>CPU1 勝利</span><strong>{stats.aWins} ({(100 * stats.aWins / stats.games).toFixed(1)}%)</strong></div>
              <div><span>CPU2 勝利</span><strong>{stats.bWins} ({(100 * stats.bWins / stats.games).toFixed(1)}%)</strong></div>
              <div><span>引き分け</span><strong>{stats.draws}</strong></div>
              <div><span>平均手数</span><strong>{stats.averageMoves.toFixed(1)}</strong></div>
              <div><span>実行時間</span><strong>{(stats.elapsedMillis / 1000).toFixed(2)} 秒</strong></div>
            </div>
          )}

          {error && <p className="error">{error}</p>}
        </aside>
      </div>
    </main>
  )
}
