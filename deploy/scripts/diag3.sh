#!/bin/bash
echo "=== gateway port mapping ==="
docker port polaris-sso-gateway 2>/dev/null
echo ""
echo "=== host listening 9080 ==="
ss -tlnp 2>/dev/null | grep 9080
echo ""
echo "=== curl gateway directly ==="
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:9080/actuator/health 2>&1
echo ""
echo "=== gateway container status ==="
docker inspect polaris-sso-gateway --format '{{.State.Status}} {{.State.Health.Status}}' 2>/dev/null
echo ""
echo "=== gateway logs tail ==="
docker logs polaris-sso-gateway --tail 25 2>&1
