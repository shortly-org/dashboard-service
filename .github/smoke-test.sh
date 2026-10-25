#!/usr/bin/env bash
# The API answers and wants a token; readiness is UP.
set -euo pipefail

kubectl --namespace shortly port-forward deploy/dashboard-service 18081:8081 18082:8082 &
forward=$!
trap 'kill $forward' EXIT
for _ in $(seq 1 30); do
  curl --silent --output /dev/null http://localhost:18082/actuator/health && break
  sleep 1
done

status=$(curl --silent --output /dev/null --write-out '%{http_code}' http://localhost:18081/api/v1/links)
[[ "$status" == 401 ]] || { echo "the API answered $status without a token, expected 401"; exit 1; }
curl --fail --silent --show-error http://localhost:18082/actuator/health/readiness | grep -q '"status":"UP"'
