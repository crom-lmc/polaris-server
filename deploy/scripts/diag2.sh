#!/bin/bash
echo "=== nginx error log (tail) ==="
tail -30 /var/log/nginx/error.log 2>/dev/null
echo ""
echo "=== master process cmdlines ==="
for pid in 1722707 2164347 2181499; do
  echo "--- pid $pid ---"
  cat /proc/$pid/cmdline 2>/dev/null | tr '\0' ' '
  echo ""
  ls -l /proc/$pid/cwd 2>/dev/null
done
echo ""
echo "=== which pid owns :80 :443 ==="
ss -tlnp 2>/dev/null | grep -E ':80 |:443 '
echo ""
echo "=== curl localhost:80 ==="
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1/ 2>&1
echo "=== curl localhost:8080 (safeline) ==="
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/ 2>&1
