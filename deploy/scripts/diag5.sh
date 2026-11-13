#!/bin/bash
echo "=== .env has SSO_INTERNAL_TOKEN? ==="
grep -c '^SSO_INTERNAL_TOKEN=' /opt/polaris/.env
echo "=== test API through nginx (expect 401, not 503) ==="
curl -s -o /dev/null -w "%{http_code}\n" --max-time 10 "https://polaris-sso.laimengchao.site/api/accounts/audit-logs?page=1&size=5"
echo "=== test an internal-style path that needs token ==="
curl -s -w "\nHTTP %{http_code}\n" --max-time 10 "http://127.0.0.1:9080/internal/..." 2>&1 | tail -3
