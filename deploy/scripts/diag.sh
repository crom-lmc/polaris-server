#!/bin/bash
echo "=== docker ps (all) ==="
docker ps --format '{{.Names}} {{.Ports}}'
echo ""
echo "=== listening ports 80/443 ==="
ss -tlnp 2>/dev/null | grep -E ':80 |:443 '
echo ""
echo "=== nginx/tengine/safeline procs ==="
ps aux | grep -iE 'nginx|tengine|safeline' | grep -v grep
echo ""
echo "=== /etc/nginx dir ==="
ls -la /etc/nginx/ 2>/dev/null
echo ""
echo "=== nginx master pid ==="
cat /run/nginx.pid 2>/dev/null
