import { useMemo, useRef, useState } from 'react'
import { Canvas, useFrame, type ThreeEvent } from '@react-three/fiber'
import { OrbitControls, useCursor } from '@react-three/drei'
import type { MeshStandardMaterial } from 'three'

type Props = {
  cells: number[][][]
  winner: number | null
  disabled: boolean
  onDrop: (x: number, y: number) => void
}

type Cell = {
  x: number
  y: number
}

type Piece = Cell & {
  z: number
  player: number
}

const SIZE = 5
const PIECE_HEIGHT = 0.34
const PIECE_RADIUS = 0.41

const positions = Array.from({ length: 25 }, (_, i) => ({
  x: i % 5,
  y: Math.floor(i / 5),
}))

const directions = [
  [1, 0, 0],
  [0, 1, 0],
  [0, 0, 1],
  [1, 1, 0],
  [1, -1, 0],
  [1, 0, 1],
  [1, 0, -1],
  [0, 1, 1],
  [0, 1, -1],
  [1, 1, 1],
  [1, 1, -1],
  [1, -1, 1],
  [1, -1, -1],
]

function cellKey(x: number, y: number, z: number) {
  return `${x}-${y}-${z}`
}

function findWinningFour(cells: number[][][], winner: number | null) {
  const result = new Set<string>()
  if (winner === null) return result

  for (let z = 0; z < SIZE; z++) {
    for (let y = 0; y < SIZE; y++) {
      for (let x = 0; x < SIZE; x++) {
        if (cells[z][y][x] !== winner) continue

        for (const [dx, dy, dz] of directions) {
          const line: string[] = []

          for (let i = 0; i < 4; i++) {
            const nx = x + dx * i
            const ny = y + dy * i
            const nz = z + dz * i

            if (
              nx < 0 || nx >= SIZE ||
              ny < 0 || ny >= SIZE ||
              nz < 0 || nz >= SIZE ||
              cells[nz][ny][nx] !== winner
            ) {
              break
            }

            line.push(cellKey(nx, ny, nz))
          }

          if (line.length === 4) {
            return new Set(line)
          }
        }
      }
    }
  }

  return result
}

function WinningMaterial({ color }: { color: string }) {
  const material = useRef<MeshStandardMaterial>(null)

  useFrame(({ clock }) => {
    if (material.current) {
      material.current.emissiveIntensity =
        0.55 + 1.8 * Math.pow(Math.max(0, Math.sin(clock.elapsedTime * 4.7)), 8)
    }
  })

  return (
    <meshStandardMaterial
      ref={material}
      color={color}
      emissive={color}
      emissiveIntensity={0.3}
      roughness={0.48}
      metalness={0.07}
    />
  )
}

function Scene({ cells, winner, disabled, onDrop }: Props) {
  const [hover, setHover] = useState<Cell | null>(null)

  useCursor(hover !== null && !disabled)

  const pieces = useMemo(() => {
    const result: Piece[] = []

    for (let z = 0; z < SIZE; z++) {
      for (let y = 0; y < SIZE; y++) {
        for (let x = 0; x < SIZE; x++) {
          const player = cells[z][y][x]

          if (player !== 0) {
            result.push({ x, y, z, player })
          }
        }
      }
    }

    return result
  }, [cells])

  const winningCells = useMemo(
    () => findWinningFour(cells, winner),
    [cells, winner],
  )

  function heightAt(x: number, y: number) {
    let height = 0

    for (let z = 0; z < SIZE; z++) {
      if (cells[z][y][x] !== 0) height++
    }

    return height
  }

  function clearHover(x: number, y: number) {
    setHover((current) =>
      current?.x === x && current.y === y ? null : current
    )
  }

  function updateHover(
    event: ThreeEvent<PointerEvent>,
    x: number,
    y: number,
    selectable: boolean,
  ) {
    event.stopPropagation()

    const topFace = (event.face?.normal.y ?? 0) > 0.9

    if (!disabled && selectable && topFace) {
      setHover((current) =>
        current?.x === x && current.y === y
          ? current
          : { x, y },
      )
    } else {
      clearHover(x, y)
    }
  }

  function place(
    event: ThreeEvent<MouseEvent>,
    x: number,
    y: number,
    selectable: boolean,
  ) {
    event.stopPropagation()

    if (
      disabled ||
      !selectable ||
      (event.face?.normal.y ?? 0) <= 0.9 ||
      event.delta >= 4
    ) {
      return
    }

    setHover(null)
    onDrop(x, y)
  }

  return (
    <>
      <color attach="background" args={['#ffffff']} />

      <ambientLight intensity={1.3} />

      <directionalLight
        position={[-4, 8, 6]}
        intensity={2}
        castShadow
        shadow-mapSize={[1024, 1024]}
      />

      <directionalLight
        position={[4, 5, -5]}
        intensity={0.6}
      />

      <mesh position={[0, -0.20, 0]} receiveShadow castShadow>
        <boxGeometry args={[5.24, 0.26, 5.24]} />
        <meshStandardMaterial color="#35454d" roughness={0.85} />
      </mesh>

      {positions.map(({ x, y }) => {
        const empty = heightAt(x, y) === 0

        return (
          <mesh
            key={`tile-${x}-${y}`}
            position={[x - 2, -0.05, y - 2]}
            receiveShadow
            onPointerOver={(event) =>
              updateHover(event, x, y, empty)
            }
            onPointerMove={(event) =>
              updateHover(event, x, y, empty)
            }
            onPointerOut={() => clearHover(x, y)}
            onClick={(event) => place(event, x, y, empty)}
          >
            <boxGeometry args={[0.94, 0.1, 0.94]} />
            <meshStandardMaterial color="#e5e9e7" roughness={0.85} />
          </mesh>
        )
      })}

      {pieces.map(({ x, y, z, player }) => {
        const height = heightAt(x, y)
        const selectable = z === height - 1 && height < SIZE
        const winning = winningCells.has(cellKey(x, y, z))
        const color = player === 1 ? '#ca704e' : '#477f9d'

        return (
          <group key={cellKey(x, y, z)}>
            <mesh
              position={[
                x - 2,
                (z + 0.5) * PIECE_HEIGHT,
                y - 2,
              ]}
              castShadow
              receiveShadow
              onPointerOver={(event) =>
                updateHover(event, x, y, selectable)
              }
              onPointerMove={(event) =>
                updateHover(event, x, y, selectable)
              }
              onPointerOut={() => clearHover(x, y)}
              onClick={(event) =>
                place(event, x, y, selectable)
              }
            >
              <cylinderGeometry
                args={[
                  PIECE_RADIUS,
                  PIECE_RADIUS,
                  PIECE_HEIGHT,
                  48,
                ]}
              />

              {winning ? (
                <WinningMaterial color={color} />
              ) : (
                <meshStandardMaterial
                  color={color}
                  roughness={0.48}
                  metalness={0.07}
                />
              )}
            </mesh>

            {winning && (
              <mesh
                position={[
                  x - 2,
                  (z + 1) * PIECE_HEIGHT + 0.012,
                  y - 2,
                ]}
                rotation={[-Math.PI / 2, 0, 0]}
              >
                <ringGeometry args={[0.33, 0.42, 48]} />
                <meshBasicMaterial
                  color="#ffebac"
                  transparent
                  opacity={0.65}
                  depthWrite={false}
                />
              </mesh>
            )}
          </group>
        )
      })}

      {hover && !disabled && heightAt(hover.x, hover.y) < SIZE && (
        <mesh
          position={[
            hover.x - 2,
            heightAt(hover.x, hover.y) * PIECE_HEIGHT + 0.012,
            hover.y - 2,
          ]}
          rotation={[-Math.PI / 2, 0, 0]}
        >
          <ringGeometry args={[0.33, 0.43, 48]} />
          <meshBasicMaterial
            color="#dfac54"
            depthWrite={false}
          />
        </mesh>
      )}

      <OrbitControls
        target={[0, 0.5, 0]}
        enablePan={false}
        enableDamping
        minDistance={5.5}
        maxDistance={15}
        minPolarAngle={0.15}
        maxPolarAngle={Math.PI / 2 - 0.08}
      />
    </>
  )
}

export default function Board3D(props: Props) {
  return (
    <Canvas
      shadows
      camera={{
        position: [5.8, 7.8, 8.5],
        fov: 40,
      }}
    >
      <Scene {...props} />
    </Canvas>
  )
}
