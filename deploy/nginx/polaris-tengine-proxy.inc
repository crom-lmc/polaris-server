# 静态站点默认回源：经雷池 tengine（127.0.0.1:8443 -> 容器 443，明文 HTTP，Host 透传）。
# 发版脚本会把它装到 polaris-cms-root.inc / polaris-batch1-root.inc / polaris-batch2-root.inc。
# 回滚（直连 9080/静态）时用 polaris-direct.inc 覆盖对应片段，再 nginx -t && nginx -s reload。
proxy_pass http://127.0.0.1:8443;
proxy_http_version 1.1;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
