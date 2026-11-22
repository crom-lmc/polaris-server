-- 开放 API 目录种子（INSERT IGNORE 幂等，可重复执行）。
-- 权限码 required_perm 对应统一登录中心 sso_permission(app_code='openapi') 的 code，
-- 由 sso-account 的 BootstrapInitializer 幂等播种；第三方应用在自己的 app_code 域下被授予这些码。

INSERT IGNORE INTO `api_open_endpoint`
  (`id`, `api_code`, `api_name`, `direction`, `app_code`, `method`, `path`, `target_url`, `auth_type`, `required_perm`, `rate_limit_per_min`, `timeout_ms`, `retry_max`, `idempotent`, `mask_rules`, `remark`, `status`, `created_at`, `updated_at`, `deleted`)
VALUES
  (202611220000000001,'inbound.master.categories','获取主数据类别',1,'settings','GET','/api/open/v1/master/categories','/internal/biz/categories','SIGNATURE','inbound:master:list',60,3000,0,1,NULL,'六类主数据类别清单',1,NOW(),NOW(),0),
  (202611220000000002,'inbound.master.items','获取主数据项',1,'settings','GET','/api/open/v1/master/{type}/items','/internal/biz/{type}','SIGNATURE','inbound:master:list',60,3000,0,1,NULL,'按类别取启用主数据项',1,NOW(),NOW(),0),
  (202611220000000003,'inbound.dict.items','获取字典项',1,'settings','GET','/api/open/v1/dict/{typeCode}/items','/internal/dicts/{typeCode}','SIGNATURE','inbound:dict:list',60,3000,0,1,NULL,'按类型取字典项',1,NOW(),NOW(),0),
  (202611220000000004,'inbound.dict.batch','批量获取字典',1,'settings','POST','/api/open/v1/dict/batch','/internal/dicts/batch','SIGNATURE','inbound:dict:list',60,3000,0,1,NULL,'一次取多类型字典',1,NOW(),NOW(),0),
  (202611220000000005,'inbound.employee.active','获取在职人员',1,'ehr','GET','/api/open/v1/employees/active','/internal/orgs/employees/active','SIGNATURE','inbound:employee:list',60,3000,0,1,'{"mobile":"mobile","email":"email"}','已开户未离职人员',1,NOW(),NOW(),0),
  (202611220000000006,'inbound.org.tree','获取组织树',1,'ehr','GET','/api/open/v1/orgs/tree','/internal/orgs/tree','SIGNATURE','inbound:org:tree',60,3000,0,1,NULL,'全量组织节点（扁平）',1,NOW(),NOW(),0),
  (202611220000000007,'inbound.org.employees','获取组织成员',1,'ehr','GET','/api/open/v1/orgs/{id}/employees','/internal/orgs/{id}/employees','SIGNATURE','inbound:employee:list',60,3000,0,1,NULL,'某组织直属已开户成员',1,NOW(),NOW(),0),
  (202611220000000008,'outbound.email.send','发送邮件',2,'email','POST','/api/open/v1/outbound/email/send','','INTERNAL','outbound:email:invoke',30,5000,0,0,'{"to":"to","subject":"subject","body":"body"}','邮件出站（SMTP 凭证托管，auth_config 配 host/port/from，credential_ref 存加密账号口令）',1,NOW(),NOW(),0);

-- P1 幂等升级：把 P0 的邮件出站占位行（status=2）升级为启用。
-- 仅匹配 P0 的原始 remark，升级后改写 remark，因此：
--   ① 新库由上面的 INSERT IGNORE 直接以 status=1 落地，本条匹配不到、不执行；
--   ② 老库占位行被升级一次后 remark 改变，后续不再匹配；
--   ③ 若运维手动停用（status=2）但 remark 已是新值，不会被误启用。
UPDATE `api_open_endpoint`
   SET `status` = 1,
       `remark` = '邮件出站（SMTP 凭证托管，auth_config 配 host/port/from，credential_ref 存加密账号口令）'
 WHERE `api_code` = 'outbound.email.send'
   AND `remark` = '邮件出站（P1 接入 SMTP 配置）'
   AND `status` = 2;

-- P1 出站渠道种子：邮件渠道。auth_config 存非敏感 SMTP 配置；
-- credential_ref 初始为空，由管理端「维护渠道凭证」接口写入 ENC(...) 密文（明文不入库）。
INSERT IGNORE INTO `api_outbound_channel`
  (`id`, `channel_code`, `channel_name`, `base_url`, `auth_type`, `credential_ref`, `auth_config`, `timeout_ms`, `retry_max`, `allowed_apps`, `status`, `remark`, `created_at`, `updated_at`, `deleted`)
VALUES
  (202611220000000101,'email','邮件渠道','','SMTP','','{"ssl":true}',5000,2,'','1','P1 邮件出站渠道，凭证由管理端后置配置',NOW(),NOW(),0);

-- P2 平台设置种子：默认空白名单（空=不限制，保持 P0/P1 行为）；管理端可配置 IP/CIDR 换行分隔。
INSERT IGNORE INTO `api_open_setting`
  (`id`, `setting_key`, `setting_value`, `remark`, `created_at`, `updated_at`, `deleted`)
VALUES
  (202611220000000201,'ip.whitelist','','第三方来源 IP 白名单，换行分隔；支持精确 IP/CIDR/*，空=不限制',NOW(),NOW(),0),
  (202611220000000202,'global.rate.limit','','全局每应用每分钟调用上限，空=不限；配置后与接口级限流取更严格者',NOW(),NOW(),0),
  (202611220000000203,'nonce.ttl.seconds','','nonce 去重窗口（秒），空=用 signature-tolerance-seconds',NOW(),NOW(),0),
  (202611220000000204,'mask.rules','','脱敏规则 JSON（手机号/身份证/邮箱/令牌开关与保留位数、maxLen），空=用默认规则',NOW(),NOW(),0);
