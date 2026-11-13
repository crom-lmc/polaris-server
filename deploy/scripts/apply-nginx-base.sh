#!/usr/bin/env bash
# 把 deploy/nginx/nginx-base.inc 幂等合入宿主机 /etc/nginx/nginx.conf
#
#   sudo deploy/scripts/apply-nginx-base.sh deploy/nginx/nginx-base.inc
#
# 合并策略（不整文件覆盖，已有配置不会被冲掉）：
#   1. 片段里每个 `# polaris-check: <正则>` 描述一组指令是否已存在；
#      已存在则保持原样并视为满足，只有缺失的指令才会被补进托管块。
#   2. 托管块用 BEGIN/END 标记包住：重复执行时先摘掉旧块再按缺失情况重建，
#      因此不会产生重复指令。
#   3. 单条例外：同路径但未带格式的 `access_log` 会被注释掉，改由托管块里的
#      `polaris_main` 接管（格式与默认 combined 逐字段一致，日志内容不变），
#      避免同一请求被记录两遍。
#   4. 只有内容确实变化才落盘，落盘前备份为 nginx.conf.bak-<时间戳>；
#      `nginx -t` 失败立即还原备份、退出 1，且不会 reload。
set -euo pipefail

CONF=${CONF:-/etc/nginx/nginx.conf}
SNIP=${1:?用法: apply-nginx-base.sh <nginx-base.inc>}
BEGIN='# >>> polaris-base BEGIN (managed by deploy/scripts/apply-nginx-base.sh)'
END='# <<< polaris-base END'

[ -f "$CONF" ] || { echo "缺少 $CONF"; exit 1; }
[ -r "$SNIP" ] || { echo "缺少片段 $SNIP"; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
TRIM="$TMP/trim.conf"
BLOCK="$TMP/block.conf"
NEW="$TMP/new.conf"
: > "$BLOCK"

# 1) 摘掉上一次的托管块，得到“基础内容”
awk -v b="$BEGIN" -v e="$END" '
  index($0, b) { skip=1; next }
  index($0, e) { skip=0; next }
  !skip { print }
' "$CONF" > "$TRIM"

# 2) 按 polaris-check 分组，把缺失的指令组写进 BLOCK
group_re=''
group_body=''
flush_group() {
  [ -n "$group_re" ] || return 0
  if ! grep -Eq "$group_re" "$TRIM"; then
    printf '%s\n' "$group_body" >> "$BLOCK"
  fi
  group_re=''
  group_body=''
}
while IFS= read -r line; do
  if [[ $line =~ ^#\ polaris-check:\ (.+)$ ]]; then
    flush_group
    group_re="${BASH_REMATCH[1]}"
  elif [ -n "$group_re" ]; then
    case "$line" in
      '' | '#'*) continue ;;
    esac
    group_body+="$line"$'\n'
  fi
done < "$SNIP"
flush_group

# 3) access_log 去重：由 polaris_main 接管同路径的未带格式行
if grep -Eq '^[[:space:]]*access_log[[:space:]]+/var/log/nginx/access\.log[[:space:]]+polaris_main[[:space:]]*;' "$BLOCK"; then
  sed -E 's|^([[:space:]]*)access_log[[:space:]]+/var/log/nginx/access\.log[[:space:]]*;|\1# access_log /var/log/nginx/access.log;  # 由 polaris-base 的 polaris_main 接管（等价 combined）|' \
    "$TRIM" > "$TMP/trim2.conf"
  mv "$TMP/trim2.conf" "$TRIM"
fi

# 4) 托管块插到 http 段的 conf.d include 之前（找不到就报错不动文件）
if ! awk -v bf="$BLOCK" '
  BEGIN { while ((getline l < bf) > 0) blk = blk l "\n" }
  !done && /include[[:space:]]+\/etc\/nginx\/conf\.d\/\*\.conf;/ { printf "%s", blk; done=1 }
  { print }
  END { if (!done) exit 2 }
' "$TRIM" > "$NEW"; then
  echo "未在 $CONF 的 http 段找到 include /etc/nginx/conf.d/*.conf;，本次不做修改"
  exit 1
fi

if cmp -s "$CONF" "$NEW"; then
  echo "nginx base 已是最新，无需变更"
  exit 0
fi

BACKUP="$CONF.bak-$(date +%Y%m%d-%H%M%S)"
cp -a "$CONF" "$BACKUP"
cat "$NEW" > "$CONF"
echo "已合入基础段，备份：$BACKUP"

if ! nginx -t; then
  cp -a "$BACKUP" "$CONF"
  echo "!!! nginx -t 失败，已还原 $CONF（备份保留：$BACKUP），未 reload"
  exit 1
fi
echo "nginx base 合入完成，nginx -t 通过"
