#!/bin/bash
printf "SHOW TABLES LIKE 'sso_audit_log';\nDESC sso_audit_log;\n" > /tmp/audit_check.sql
docker exec -i blob-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" sso_ms' < /tmp/audit_check.sql
