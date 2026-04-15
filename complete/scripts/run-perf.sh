#!/usr/bin/env bash
#
# Run the Gatling FormValidationSimulation against the app in each of the
# three A/B/C Spring profiles, producing one Gatling HTML report per leg.
#
# Usage:
#   scripts/run-perf.sh [rps] [duration_seconds] [warmup_seconds]
#
# Defaults: rps=100, duration=120, warmup=30.
#
# Output:
#   target/gatling/<profile>-<timestamp>/index.html   (per leg)
#
# Requires: Java 21, Maven wrapper, and a free port 8080.

set -euo pipefail

RPS="${1:-100}"
DURATION="${2:-120}"
WARMUP="${3:-30}"
PORT="${PORT:-8080}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# profile:payloadShape pairs
LEGS=(
  "ext-off:shallow"
  "ext-on-shallow:shallow"
  "ext-on-deep:deep"
)

echo "==> Packaging application (skipping tests)"
./mvnw -q -DskipTests package

JAR="$(ls target/validating-form-input-*.jar | head -n 1)"
if [[ -z "${JAR}" ]]; then
  echo "Could not find built jar under target/" >&2
  exit 1
fi

mkdir -p target/gatling-runs

for leg in "${LEGS[@]}"; do
  PROFILE="${leg%%:*}"
  SHAPE="${leg##*:}"
  STAMP="$(date +%Y%m%d-%H%M%S)"

  LOG="target/gatling-runs/app-${PROFILE}-${STAMP}.log"

  echo
  echo "==> [${PROFILE}] starting app on :${PORT} (shape=${SHAPE})"
  java -Dspring.profiles.active="${PROFILE}" \
       -Dserver.port="${PORT}" \
       -jar "${JAR}" \
       > "${LOG}" 2>&1 &
  APP_PID=$!

  # wait for /actuator/health to be UP
  echo "    waiting for health on :${PORT} (pid=${APP_PID})"
  for i in $(seq 1 60); do
    if curl -fsS "http://localhost:${PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "    app is UP after ${i}s"
      break
    fi
    if ! kill -0 "${APP_PID}" 2>/dev/null; then
      echo "    app died during startup; tail of log:" >&2
      tail -n 40 "${LOG}" >&2
      exit 1
    fi
    sleep 1
  done

  echo "==> [${PROFILE}] running Gatling: rps=${RPS} duration=${DURATION}s warmup=${WARMUP}s shape=${SHAPE}"
  ./mvnw -q -Pperformance gatling:test \
      -DbaseUrl="http://localhost:${PORT}" \
      -Drps="${RPS}" \
      -Dduration="${DURATION}" \
      -Dwarmup="${WARMUP}" \
      -DpayloadShape="${SHAPE}" \
      -Drun.label="${PROFILE}" \
    || { echo "Gatling failed for ${PROFILE}"; kill "${APP_PID}" 2>/dev/null || true; exit 1; }

  # Move the just-generated report aside so the next leg does not overwrite it.
  LATEST_REPORT="$(ls -td target/gatling/formvalidationsimulation-* 2>/dev/null | head -n 1 || true)"
  if [[ -n "${LATEST_REPORT}" ]]; then
    DEST="target/gatling/${PROFILE}-${STAMP}"
    mv "${LATEST_REPORT}" "${DEST}"
    echo "    report: ${DEST}/index.html"
  fi

  echo "==> [${PROFILE}] stopping app (pid=${APP_PID})"
  kill "${APP_PID}" 2>/dev/null || true
  wait "${APP_PID}" 2>/dev/null || true
done

echo
echo "==> All runs complete. Reports under target/gatling/"
ls -1 target/gatling/
