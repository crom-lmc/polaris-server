# ============================================================
# Polaris 宿主机 nginx 基础段（http 上下文）
# ------------------------------------------------------------
# 这些指令原先只存在于服务器 /etc/nginx/nginx.conf，重装/换机会丢，
# 导致 api-proxy.inc 引用未定义的 zone 而 nginx -t 失败。
#
# 合入方式：deploy/scripts/apply-nginx-base.sh 幂等合入，不整文件覆盖
#   - 每个 `# polaris-check:` 后面的正则描述“该指令组是否已存在”
#   - 已存在则保持原样（视为已满足），缺失才在托管块内补一行
#   - 托管块用 BEGIN/END 标记包住，重复执行不会产生重复内容
#   - 改动前备份 nginx.conf.bak-<时间戳>；nginx -t 失败立即还原并退出 1
#
# 注意：polaris_main 与 nginx 默认 combined 格式逐字段一致，
#      切换后访问日志内容不变；脚本会把同路径的未带格式 access_log 注释掉，
#      避免同一请求被记录两遍。
# ============================================================

# polaris-check: ^[[:space:]]*server_tokens[[:space:]]+off[[:space:]]*;
server_tokens off;

# polaris-check: ^[[:space:]]*limit_req_zone[[:space:]].*zone=api_limit
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

# polaris-check: ^[[:space:]]*limit_req_zone[[:space:]].*zone=login_limit
limit_req_zone $binary_remote_addr zone=login_limit:10m rate=3r/s;

# polaris-check: ^[[:space:]]*limit_conn_zone[[:space:]].*zone=conn_per_ip
limit_conn_zone $binary_remote_addr zone=conn_per_ip:10m;

# polaris-check: ^[[:space:]]*map[[:space:]]+\$http_user_agent[[:space:]]+\$bad_bot
map $http_user_agent $bad_bot {
    default 0;
    ~*(nikto|sqlmap|nmap|masscan|acunetix|nessus|dirbuster|gobuster|wfuzz|zgrab|nuclei) 1;
}

# polaris-check: ^[[:space:]]*sendfile[[:space:]]+on[[:space:]]*;
sendfile on;

# polaris-check: ^[[:space:]]*tcp_nopush[[:space:]]+on[[:space:]]*;
tcp_nopush on;

# polaris-check: ^[[:space:]]*types_hash_max_size[[:space:]]+2048[[:space:]]*;
types_hash_max_size 2048;

# polaris-check: ^[[:space:]]*default_type[[:space:]]+application/octet-stream[[:space:]]*;
default_type application/octet-stream;

# polaris-check: ^[[:space:]]*ssl_protocols[[:space:]]+TLSv1\.2[[:space:]]+TLSv1\.3[[:space:]]*;
ssl_protocols TLSv1.2 TLSv1.3;

# polaris-check: ^[[:space:]]*ssl_prefer_server_ciphers[[:space:]]+off[[:space:]]*;
ssl_prefer_server_ciphers off;

# polaris-check: ^[[:space:]]*gzip[[:space:]]+on[[:space:]]*;
gzip on;

# polaris-check: ^[[:space:]]*log_format[[:space:]]+polaris_main
log_format polaris_main '$remote_addr - $remote_user [$time_local] "$request" '
                        '$status $body_bytes_sent "$http_referer" '
                        '"$http_user_agent"';

# polaris-check: ^[[:space:]]*access_log[[:space:]]+/var/log/nginx/access\.log[[:space:]]+polaris_main[[:space:]]*;
access_log /var/log/nginx/access.log polaris_main;
