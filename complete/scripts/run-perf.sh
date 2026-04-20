#!/usr/bin/env bash
#
# Run the Gatling FormValidationSimulation against the app in a six-leg
# matrix: off/shallow/deep Spring profiles x map/raw body modes, always using
# the same deep shopping-cart payload.
#
# Usage:
#   scripts/run-perf.sh [rps] [duration_seconds] [warmup_seconds]
#
# Defaults: rps=100, duration=120, warmup=30.
#
# Output:
#   target/gatling/<profile>-<bodyMode>-deep-<timestamp>/index.html   (per leg)
#
# Requires: Java 21, Maven wrapper, and a free port 8080.

set -euo pipefail

RPS="${1:-100}"
DURATION="${2:-120}"
WARMUP="${3:-30}"
PORT="${PORT:-8080}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# profile:bodyMode pairs
LEGS=(
  "ext-off:map"
  "ext-on-shallow:map"
  "ext-on-deep:map"
  "ext-off:raw"
  "ext-on-shallow:raw"
  "ext-on-deep:raw"
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
  BODY_MODE="${leg##*:}"
  SHAPE="deep"
  STAMP="$(date +%Y%m%d-%H%M%S)"

  LOG="target/gatling-runs/app-${PROFILE}-${BODY_MODE}-${SHAPE}-${STAMP}.log"

  echo
  echo "==> [${PROFILE}/${BODY_MODE}] starting app on :${PORT} (shape=${SHAPE})"
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

  echo "==> [${PROFILE}/${BODY_MODE}] running Gatling: rps=${RPS} duration=${DURATION}s warmup=${WARMUP}s bodyMode=${BODY_MODE} shape=${SHAPE}"
  ./mvnw -q -Pperformance gatling:test \
      -DbaseUrl="http://localhost:${PORT}" \
      -Drps="${RPS}" \
      -Dduration="${DURATION}" \
      -Dwarmup="${WARMUP}" \
      -DbodyMode="${BODY_MODE}" \
      -DpayloadShape="${SHAPE}" \
      -Drun.label="${PROFILE}-${BODY_MODE}-${SHAPE}" \
    || { echo "Gatling failed for ${PROFILE}/${BODY_MODE}"; kill "${APP_PID}" 2>/dev/null || true; exit 1; }

  # Move the just-generated report aside so the next leg does not overwrite it.
  LATEST_REPORT="$(ls -td target/gatling/formvalidationsimulation-* 2>/dev/null | head -n 1 || true)"
  if [[ -n "${LATEST_REPORT}" ]]; then
    DEST="target/gatling/${PROFILE}-${BODY_MODE}-${SHAPE}-${STAMP}"
    mv "${LATEST_REPORT}" "${DEST}"
    echo "    report: ${DEST}/index.html"
  fi

  echo "==> [${PROFILE}/${BODY_MODE}] stopping app (pid=${APP_PID})"
  kill "${APP_PID}" 2>/dev/null || true
  wait "${APP_PID}" 2>/dev/null || true
done

echo
echo "==> All runs complete. Reports under target/gatling/"
ls -1 target/gatling/
