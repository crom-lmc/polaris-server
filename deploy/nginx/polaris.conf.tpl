# Polarís 生产 Nginx 配置（模板）
# 生成：envsubst '${DOMAIN}' < polaris.conf.tpl > /etc/nginx/conf.d/polaris.conf
# 依赖：/etc/nginx/snippets/polaris-api.inc（api-proxy.inc 上传后改名）
# 证书：每个域名单独一张（腾讯云签发），已解压到 /etc/nginx/certs/<域名>/<域名>_nginx/

upstream polaris_gateway {
    server 127.0.0.1:9080;
    keepalive 16;
}

# 所有站点统一继承安全响应头；always 确保错误响应同样携带。
add_header X-Frame-Options "DENY" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "object-src 'none'; base-uri 'self'; frame-ancestors 'none'" always;
add_header Strict-Transport-Security "max-age=31536000" always;

server {
    listen 80;
    server_name
        polaris-sso.${DOMAIN}
        polaris-ehr.${DOMAIN}
        polaris-cms.${DOMAIN}
        polaris-settings.${DOMAIN}
        polaris-job.${DOMAIN}
        polaris-portal.${DOMAIN}
        polaris-gateway.${DOMAIN}
        polaris-auth.${DOMAIN}
        polaris-account.${DOMAIN}
        portal-blog-api.${DOMAIN}
        portal-blog-web.${DOMAIN}
        portal-blog-admin.${DOMAIN};
    return 301 https://$host$request_uri;
}

# ============ 静态站点（8 个）============

server {
    listen 443 ssl;
    server_name polaris-sso.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-sso.${DOMAIN}/polaris-sso.${DOMAIN}_nginx/polaris-sso.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-sso.${DOMAIN}/polaris-sso.${DOMAIN}_nginx/polaris-sso.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    root /opt/polaris/www/sso;
    index index.html;

    include /etc/nginx/snippets/polaris-api.inc;

    location / {
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 443 ssl;
    server_name polaris-ehr.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-ehr.${DOMAIN}/polaris-ehr.${DOMAIN}_nginx/polaris-ehr.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-ehr.${DOMAIN}/polaris-ehr.${DOMAIN}_nginx/polaris-ehr.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    root /opt/polaris/www/ehr;
    index index.html;

    include /etc/nginx/snippets/polaris-api.inc;

    location /uploads/ {
        proxy_pass http://127.0.0.1:9080;
        proxy_set_header Host $host;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 443 ssl;
    server_name polaris-cms.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-cms.${DOMAIN}/polaris-cms.${DOMAIN}_nginx/polaris-cms.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-cms.${DOMAIN}/polaris-cms.${DOMAIN}_nginx/polaris-cms.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    root /opt/polaris/www/cms;
    index index.html;

    include /etc/nginx/snippets/polaris-api.inc;

    location /uploads/ {
        proxy_pass http://127.0.0.1:9080;
        proxy_set_header Host $host;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 443 ssl;
    server_name polaris-settings.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-settings.${DOMAIN}/polaris-settings.${DOMAIN}_nginx/polaris-settings.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-settings.${DOMAIN}/polaris-settings.${DOMAIN}_nginx/polaris-settings.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    root /opt/polaris/www/settings;
    index index.html;

    include /etc/nginx/snippets/polaris-api.inc;

    location / {
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 443 ssl;
    server_name polaris-job.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-job.${DOMAIN}/polaris-job.${DOMAIN}_nginx/polaris-job.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-job.${DOMAIN}/polaris-job.${DOMAIN}_nginx/polaris-job.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    root /opt/polaris/www/job;
    index index.html;

    include /etc/nginx/snippets/polaris-api.inc;

    location / {
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 443 ssl;
    server_name polaris-portal.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-portal.${DOMAIN}/polaris-portal.${DOMAIN}_nginx/polaris-portal.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-portal.${DOMAIN}/polaris-portal.${DOMAIN}_nginx/polaris-portal.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    root /opt/polaris/www/portal;
    index index.html;

    include /etc/nginx/snippets/polaris-api.inc;

    location /uploads/ {
        proxy_pass http://127.0.0.1:9080;
        proxy_set_header Host $host;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

# 博客后台（hash 路由，回调 /#/sso/callback）
server {
    listen 443 ssl;
    server_name portal-blog-admin.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/portal-blog-admin.${DOMAIN}/portal-blog-admin.${DOMAIN}_nginx/portal-blog-admin.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/portal-blog-admin.${DOMAIN}/portal-blog-admin.${DOMAIN}_nginx/portal-blog-admin.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    root /opt/polaris/www/blog-admin;
    index index.html;

    include /etc/nginx/snippets/polaris-api.inc;

    location /uploads/ {
        proxy_pass http://127.0.0.1:9080;
        proxy_set_header Host $host;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

# 博客读者端（公开站点）
server {
    listen 443 ssl;
    server_name portal-blog-web.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/portal-blog-web.${DOMAIN}/portal-blog-web.${DOMAIN}_nginx/portal-blog-web.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/portal-blog-web.${DOMAIN}/portal-blog-web.${DOMAIN}_nginx/portal-blog-web.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    root /opt/polaris/www/blog-web;
    index index.html;

    include /etc/nginx/snippets/polaris-api.inc;

    location /uploads/ {
        proxy_pass http://127.0.0.1:9080;
        proxy_set_header Host $host;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

# ============ 后端直连域名（4 个，便于自测/直连）============

server {
    listen 443 ssl;
    server_name polaris-gateway.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-gateway.${DOMAIN}/polaris-gateway.${DOMAIN}_nginx/polaris-gateway.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-gateway.${DOMAIN}/polaris-gateway.${DOMAIN}_nginx/polaris-gateway.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:9080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}

server {
    listen 443 ssl;
    server_name polaris-auth.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-auth.${DOMAIN}/polaris-auth.${DOMAIN}_nginx/polaris-auth.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-auth.${DOMAIN}/polaris-auth.${DOMAIN}_nginx/polaris-auth.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:9001;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}

server {
    listen 443 ssl;
    server_name polaris-account.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/polaris-account.${DOMAIN}/polaris-account.${DOMAIN}_nginx/polaris-account.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/polaris-account.${DOMAIN}/polaris-account.${DOMAIN}_nginx/polaris-account.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:9002;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}

# 博客服务直连（含 /uploads 静态图）
server {
    listen 443 ssl;
    server_name portal-blog-api.${DOMAIN};
    ssl_certificate     /etc/nginx/certs/portal-blog-api.${DOMAIN}/portal-blog-api.${DOMAIN}_nginx/portal-blog-api.${DOMAIN}_bundle.crt;
    ssl_certificate_key /etc/nginx/certs/portal-blog-api.${DOMAIN}/portal-blog-api.${DOMAIN}_nginx/portal-blog-api.${DOMAIN}.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:9003;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}

