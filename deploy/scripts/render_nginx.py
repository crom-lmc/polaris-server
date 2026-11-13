import sys

token = None
with open("/opt/polaris/.env") as f:
    for line in f:
        if line.startswith("SSO_INTERNAL_TOKEN="):
            token = line.strip().split("=", 1)[1]
            break
assert token, "SSO_INTERNAL_TOKEN not found"

with open(sys.argv[1]) as f:
    tpl = f.read()

out = tpl.replace("${DOMAIN}", "laimengchao.site").replace("${SSO_INTERNAL_TOKEN}", token)
with open(sys.argv[2], "w") as f:
    f.write(out)
print("rendered OK, token len:", len(token))
