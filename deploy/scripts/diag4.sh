#!/bin/bash
echo "=== curl domains from server ==="
for d in polaris-sso.laimengchao.site polaris-ehr.laimengchao.site portal-blog-web.laimengchao.site polaris-cms.laimengchao.site; do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 8 "https://$d/" 2>&1)
  echo "$d -> $code"
done
echo ""
echo "=== safeline mgmt port 9443 ==="
curl -sk -o /dev/null -w "%{http_code}\n" --max-time 8 https://127.0.0.1:9443/ 2>&1
echo ""
echo "=== gateway container env (internal token) ==="
docker exec polaris-sso-gateway env 2>/dev/null | grep -i SSO_INTERNAL
echo ""
echo "=== gateway internal-token config via actuator/env (if exposed) ==="
curl -s --max-time 5 http://127.0.0.1:9080/actuator/env 2>&1 | head -c 300
echo ""
echo "=== recent nginx errors (last 3 min) ==="
tail -15 /var/log/nginx/error.log 2>/dev/null
