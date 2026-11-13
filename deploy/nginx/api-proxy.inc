# 所有前端站点共用的 API 反代片段：/api 走本机网关，同源、无跨域
# 限流 zone（api_limit / login_limit / conn_per_ip）与 $bad_bot map 由
# deploy/nginx/nginx-base.inc 定义，发布脚本用 apply-nginx-base.sh 幂等合入
# 宿主机 /etc/nginx/nginx.conf；缺失时 nginx -t 会失败。
location /api/ {
    limit_req zone=api_limit burst=30 nodelay;
    limit_req_status 429;
    proxy_pass http://127.0.0.1:9080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
}
