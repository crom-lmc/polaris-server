#!/usr/bin/env bash
# 2C4G 服务器准备：swap + 内核参数 + 目录。以 root 执行一次即可。
set -euo pipefail

echo "== 1. swap（4G 物理内存必须留兜底，建议 2G）=="
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo "  swap 2G 已启用"
else
  echo "  swap 已存在，跳过"
fi
sysctl -w vm.swappiness=20
grep -q 'vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=20' >> /etc/sysctl.conf

echo "== 2. 网络与连接数 =="
cat > /etc/sysctl.d/99-polaris.conf <<'EOF'
net.core.somaxconn = 1024
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 30
vm.overcommit_memory = 0
fs.file-max = 100000
EOF
sysctl --system >/dev/null

echo "== 3. 目录 =="
mkdir -p /opt/polaris/{jars,www,keys,logs,uploads/blog}
chmod 700 /opt/polaris/keys

echo "== 4. 限制 journald 占用（磁盘小的话很重要）=="
mkdir -p /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/size.conf <<'EOF'
[Journal]
SystemMaxUse=200M
RuntimeMaxUse=100M
EOF
systemctl restart systemd-journald || true

echo "== 5. 检查依赖 =="
docker version --format '  docker {{.Server.Version}}' || echo "  ! docker 未就绪"
docker compose version || echo "  ! 需要 docker compose v2"
nginx -v 2>&1 | sed 's/^/  /'
mysql --version | sed 's/^/  /'
redis-cli ping 2>/dev/null | sed 's/^/  redis: /' || echo "  ! redis 未响应"

echo "== 完成；剩余内存 =="
free -m
