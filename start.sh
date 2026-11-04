#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"

command -v java >/dev/null
command -v npm >/dev/null
command -v curl >/dev/null
command -v setsid >/dev/null

test -f "$project_root/backend/pom.xml"
test -f "$project_root/frontend/package.json"

if [ ! -d "$project_root/frontend/node_modules" ]; then
    echo "フロントエンドの部品をインストールしています..."
    npm --prefix "$project_root/frontend" ci
fi

if curl -s -o /dev/null --max-time 1 http://127.0.0.1:8080/; then
    echo "ERROR: 8080番ポートが使用中です。Javaサーバーを停止してください。"
    exit 1
fi

if curl -s -o /dev/null --max-time 1 http://127.0.0.1:5173/; then
    echo "ERROR: 5173番ポートが使用中です。Reactサーバーを停止してください。"
    exit 1
fi

backend_pid=""
frontend_pid=""

stop_servers() {
    trap - EXIT

    echo
    echo "サーバーを停止しています..."

    if [ -n "$frontend_pid" ]; then
        kill -- "-$frontend_pid" 2>/dev/null || true
    fi

    if [ -n "$backend_pid" ]; then
        kill -- "-$backend_pid" 2>/dev/null || true
    fi

    wait 2>/dev/null || true
}

trap stop_servers EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo "Javaサーバーを起動しています..."

setsid bash -c '
    cd "$1"
    exec ./mvnw spring-boot:run
' bash "$project_root/backend" &

backend_pid=$!

backend_ready=0

for i in {1..120}; do
    if curl -s -o /dev/null --max-time 1 http://127.0.0.1:8080/; then
        backend_ready=1
        break
    fi

    if ! kill -0 "$backend_pid" 2>/dev/null; then
        echo "ERROR: Javaサーバーが終了しました。"
        exit 1
    fi

    sleep 0.5
done

if [ "$backend_ready" -ne 1 ]; then
    echo "ERROR: Javaサーバーの起動がタイムアウトしました。"
    exit 1
fi

echo "Reactサーバーを起動しています..."

setsid bash -c '
    cd "$1"
    exec npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
' bash "$project_root/frontend" &

frontend_pid=$!

frontend_ready=0

for i in {1..40}; do
    if curl -s -o /dev/null --max-time 1 http://127.0.0.1:5173/; then
        frontend_ready=1
        break
    fi

    if ! kill -0 "$frontend_pid" 2>/dev/null; then
        echo "ERROR: Reactサーバーが終了しました。"
        exit 1
    fi

    sleep 0.25
done

if [ "$frontend_ready" -ne 1 ]; then
    echo "ERROR: Reactサーバーの起動がタイムアウトしました。"
    exit 1
fi

echo
echo "======================================"
echo "  立体四目並べ 起動完了"
echo "  http://localhost:5173"
echo "  終了するには Ctrl + C"
echo "======================================"
echo

wait -n "$backend_pid" "$frontend_pid"
