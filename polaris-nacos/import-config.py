#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 nacos/config/<env>/*.yaml 推送到 Nacos 对应命名空间（走官方 OpenAPI，无需 SDK）。

环境与命名空间一一对应：
  local  →  sso-ms-local     本地调试用
  prod   →  sso-ms-prod      线上用

常用：
  python nacos/import-config.py --env local              # 推送本地环境配置
  python nacos/import-config.py --env prod --dry-run     # 只打印将推送什么
  python nacos/import-config.py --env local --list       # 看命名空间里现有配置
  python nacos/import-config.py --env local --addr 127.0.0.1:18848 --user nacos --pwd xxx

连接参数的默认值按顺序取自环境变量：
  SSO_NACOS_ADDR / NACOS_SERVER_ADDR      （默认 127.0.0.1:8848）
  SSO_NACOS_USER / NACOS_USERNAME         （默认 nacos）
  SSO_NACOS_PASSWORD / NACOS_PASSWORD     （默认空 → 不登录，适合关闭鉴权的实例）

推送完成后会回读一次并逐字节比对，确认服务真能拉到同样的内容。

敏感项（数据库口令、服务间令牌、JWT 私钥等）在配置文件里是 ENC(...) 密文。
推送前会用主密钥试解一遍：解不开就不推——避免把「打不开的密文」发上生产，
那种情况下服务启动会直接失败，而排查方向往往会被误判成 Nacos 的问题。
主密钥来自环境变量 SSO_CONF_KEY（或 SSO_CONF_KEY_FILE），也可先 `source nacos/.env`。
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG_ROOT = os.path.join(HERE, "config")
SECRET_TOOL = os.path.join(os.path.dirname(HERE), "tools", "SecretTool.java")
DATA_IDS = [
    "sso-gateway.yaml", "sso-auth.yaml", "sso-account.yaml",
    "blog-admin.yaml", "cms.yaml", "ehr.yaml", "settings.yaml", "portal.yaml", "job.yaml",
]
ENVS = {"local": "sso-ms-local", "prod": "sso-ms-prod"}
SEALED_PREFIX = "ENC("


def master_key():
    raw = os.environ.get("SSO_CONF_KEY", "").strip()
    if raw:
        return raw
    path = os.environ.get("SSO_CONF_KEY_FILE", "").strip()
    if path and os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return f.read().strip()
    return ""


def sealed_entries(content):
    """取出 (配置项名, 密文) —— 只看真正的 key: value 行，注释里出现字样不算。"""
    for line in content.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or ":" not in stripped:
            continue
        name, value = stripped.split(":", 1)
        value = value.strip()
        if value.startswith(SEALED_PREFIX) and value.endswith(")"):
            yield name.strip(), value


def verify_sealed(name, content):
    entries = list(sealed_entries(content))
    if not entries:
        return True
    key = master_key()
    if not key:
        print("[WARN] %s 含 %d 个加密项，但未提供 SSO_CONF_KEY，跳过解密校验"
              "（部署时缺主密钥会启动失败）" % (name, len(entries)))
        return True
    java = shutil.which("java")
    if not java or not os.path.exists(SECRET_TOOL):
        print("[WARN] 找不到 java 或 tools/SecretTool.java，跳过密文校验")
        return True
    env = dict(os.environ, SSO_CONF_KEY=key)
    for key_name, value in entries:
        result = subprocess.run([java, SECRET_TOOL, "decrypt", value],
                                capture_output=True, env=env)
        if result.returncode != 0:
            err = (result.stderr or b"").decode("utf-8", "ignore").strip().splitlines()
            print("[FAIL] %s 的 %s 解不开：%s" % (name, key_name, err[-1] if err else "未知错误"))
            print("       多半是换过主密钥却没重加密，或这份配置从别处拷来。")
            print("       重加密：java tools/SecretTool.java encrypt --stdin")
            return False
    print("[OK] %-18s 密文校验通过（%d 项）" % (name, len(entries)))
    return True


def env_of(*names, default=""):
    for n in names:
        v = os.environ.get(n)
        if v:
            return v
    return default


def http(method, url, form=None):
    data = None
    headers = {}
    if form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    # 显式禁用系统代理：Windows 上设了 HTTP_PROXY 时 urllib 会把 127.0.0.1 也送进代理
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=15) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except urllib.error.URLError as e:
        return 0, "连接失败: %s" % e.reason


def login(base, user, pwd):
    """Nacos 开启鉴权（auth_enabled=true）时必须先换 accessToken，否则配置接口一律 403。"""
    if not user or not pwd:
        return None
    status, body = http("POST", base + "/v1/auth/login", {"username": user, "password": pwd})
    if status != 200:
        print("登录失败（%s）：%s" % (status, body[:200]))
        sys.exit(2)
    try:
        token = json.loads(body).get("accessToken")
    except json.JSONDecodeError:
        print("登录返回不是 JSON：%s" % body[:200])
        sys.exit(2)
    if not token:
        print("登录未返回 accessToken：%s" % body[:200])
        sys.exit(2)
    print("已登录 Nacos（%s）" % user)
    return token


def ensure_namespace(base, ns_id, token, create):
    status, body = http("GET", base + "/v1/console/namespaces")
    if status != 200:
        print("读取命名空间失败（%s）：%s" % (status, body[:200]))
        sys.exit(2)
    existing = {n.get("namespace") for n in json.loads(body).get("data", [])}
    if ns_id in existing:
        print("命名空间已存在：%s" % ns_id)
        return True
    if not create:
        print("命名空间不存在：%s（加 --create-namespace 自动创建）" % ns_id)
        return False
    form = {"customNamespaceId": ns_id, "namespaceName": ns_id,
            "namespaceDesc": "SSO 微服务" + ("本地调试环境" if ns_id.endswith("local") else "生产环境")}
    if token:
        form["accessToken"] = token
    status, body = http("POST", base + "/v1/console/namespaces", form)
    if status == 200 and body.strip().lower() == "true":
        print("已创建命名空间：%s" % ns_id)
        return True
    print("创建命名空间失败（%s）：%s" % (status, body[:200]))
    return False


def list_configs(base, ns_id, group, token):
    # 注意：search=accurate 时 dataId/group 必须精确匹配，空值只会返回 0 条；
    # 想看「全部」得用 search=blur + 通配符 *（服务端会转成 %）。
    q = "/v1/cs/configs?" + urllib.parse.urlencode(
        {"search": "blur", "dataId": "*", "group": "*", "pageNo": 1, "pageSize": 100, "tenant": ns_id})
    if token:
        q += "&accessToken=" + urllib.parse.quote(token)
    status, body = http("GET", base + q)
    if status != 200:
        print("查询配置列表失败（%s）：%s" % (status, body[:200]))
        return
    items = json.loads(body).get("pageItems") or []
    if group:
        items = [i for i in items if i.get("group") == group]
    if not items:
        print("命名空间 %s 下暂无配置" % ns_id)
        return
    print("命名空间 %s 下共 %d 条配置：" % (ns_id, len(items)))
    for it in items:
        print("  %-20s group=%-12s %d bytes  %s"
              % (it.get("dataId"), it.get("group"), len(it.get("content") or ""), it.get("type")))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--env", default="local", choices=sorted(ENVS), help="配置环境，决定读取哪个目录与命名空间")
    ap.add_argument("--addr", default=env_of("SSO_NACOS_ADDR", "NACOS_SERVER_ADDR", default="127.0.0.1:8848"))
    ap.add_argument("--group", default=env_of("SSO_NACOS_GROUP", default="SSO"))
    ap.add_argument("--namespace", default=None, help="覆盖命名空间 ID（默认 sso-ms-<env>）")
    ap.add_argument("--user", default=env_of("SSO_NACOS_USER", "NACOS_USERNAME", default="nacos"))
    ap.add_argument("--pwd", default=env_of("SSO_NACOS_PASSWORD", "NACOS_PASSWORD"))
    ap.add_argument("--create-namespace", action="store_true", help="命名空间不存在时自动创建")
    ap.add_argument("--list", action="store_true", help="只列出命名空间内已有配置")
    ap.add_argument("--dry-run", action="store_true", help="只打印，不写入")
    ap.add_argument("--skip-seal-check", action="store_true", help="跳过推送前的密文可解校验")
    args = ap.parse_args()

    ns_id = args.namespace or ENVS[args.env]
    cfg_dir = os.path.join(CONFIG_ROOT, args.env)
    base = "http://%s/nacos" % args.addr

    if not os.path.isdir(cfg_dir):
        print("配置目录不存在：%s" % cfg_dir)
        sys.exit(2)

    if not args.dry_run:
        status, body = http("GET", base + "/v1/console/health/readiness")
        if status != 200:
            print("Nacos 不可达（%s）：%s" % (status, body[:200]))
            print("检查：① 服务已启动 ② 端口是 HTTP 端口 ③ SSH 隧道还在")
            sys.exit(2)
        print("Nacos 就绪：%s" % args.addr)

    token = None if args.dry_run else login(base, args.user, args.pwd)

    if args.list:
        if not token and args.user:
            sys.exit(2)
        list_configs(base, ns_id, args.group, token)
        return

    if not args.dry_run and not ensure_namespace(base, ns_id, token, args.create_namespace):
        sys.exit(2)

    ok = 0
    files = [n for n in DATA_IDS if os.path.exists(os.path.join(cfg_dir, n))]
    missing = [n for n in DATA_IDS if n not in files]
    for name in missing:
        print("[SKIP] %s 不存在（%s）" % (name, cfg_dir))

    for name in files:
        with open(os.path.join(cfg_dir, name), encoding="utf-8") as f:
            content = f.read()

        if args.dry_run:
            print("\n--- env=%s ns=%s %s (group=%s, %d bytes) ---\n%s"
                  % (args.env, ns_id, name, args.group, len(content), content))
            continue

        if not args.skip_seal_check and not verify_sealed(name, content):
            continue

        form = {"dataId": name, "group": args.group, "content": content, "type": "yaml", "tenant": ns_id}
        if token:
            form["accessToken"] = token
        status, body = http("POST", base + "/v1/cs/configs", form)
        if not (status == 200 and body.strip().lower() == "true"):
            print("[FAIL] %s status=%s body=%s" % (name, status, body[:200]))
            continue

        # 回读校验：确认服务端存的就是这份内容（而不是写到了别的命名空间）
        q = "/v1/cs/configs?" + urllib.parse.urlencode(
            {"dataId": name, "group": args.group, "tenant": ns_id})
        if token:
            q += "&accessToken=" + urllib.parse.quote(token)
        s2, back = http("GET", base + q)
        same = s2 == 200 and back == content
        print("[OK] %-18s env=%-5s ns=%-14s 回读%s" % (name, args.env, ns_id, "一致" if same else "不一致!"))
        if same:
            ok += 1

    if args.dry_run:
        print("\n（dry-run：未实际推送）")
        return

    print("\n完成：%d/%d 条配置已发布到 %s" % (ok, len(files), ns_id))
    print("启动服务时设置：SSO_NACOS_ENABLED=true SSO_NACOS_NAMESPACE=%s" % ns_id)
    sys.exit(0 if ok == len(files) and files else 1)


if __name__ == "__main__":
    main()
