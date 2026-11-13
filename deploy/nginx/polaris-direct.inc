# 回滚片段：静态站点由本机 nginx 直接服务，不经雷池。
# 用法：sudo install -m 644 /opt/polaris/polaris-direct.inc /etc/nginx/snippets/polaris-batch1-root.inc
#       （batch2/cms 同理），然后 sudo nginx -t && sudo nginx -s reload。
try_files $uri $uri/ /index.html;
