INSERT INTO forum_thread (id,kind,board_key,title,content,excerpt,tags,author_account_id,author_name,reward,replies_count,views_count,likes_count,pinned,solved,created_at)
SELECT seed.* FROM (
SELECT 101 AS id,'topic' AS kind,'backend' AS board_key,'从一次线上超时说起：我们如何梳理微服务调用链' AS title,'一次看似普通的接口超时，最终牵出了连接池、重试和网关超时配置之间的问题。\n\n排查复杂问题时，先建立统一的时间线，再讨论具体原因。通过调用链确认网关、下游服务、连接池和重试的实际耗时，最后统一各层超时并限制非幂等请求重试。' AS content,'一次看似普通的接口超时，最终牵出了连接池、重试和网关超时配置之间的问题，记录完整排查过程。' AS excerpt,'["微服务","问题排查","Spring Boot"]' AS tags,0 AS author_account_id,'林宇辰' AS author_name,0 AS reward,3 AS replies_count,1268 AS views_count,96 AS likes_count,1 AS pinned,1 AS solved,DATE_SUB(NOW(),INTERVAL 2 HOUR) AS created_at
UNION ALL SELECT 102,'topic','frontend','Vue 3 大型表单拆分的几种实践，你更喜欢哪一种？','从组件通信、校验时机和草稿恢复三个角度，对比 provide/inject、组合式函数与集中状态管理的实际体验。欢迎分享你在复杂表单中的拆分方式。','从组件通信、校验时机和草稿恢复三个角度，对比不同组件通信方案的实际体验。','["Vue 3","TypeScript","工程化"]',0,'周清妍',0,24,856,72,0,0,DATE_SUB(NOW(),INTERVAL 3 HOUR)
UNION ALL SELECT 103,'topic','devops','分享一份轻量 Docker 发布检查清单','不追求复杂平台，只用 Compose 和 Nginx 也能把构建、发布、健康检查与回滚做得清晰可靠。','不追求复杂平台，只用 Compose 和 Nginx 也能把构建、发布、健康检查与回滚做得清晰可靠。','["Docker","Nginx","发布"]',0,'陈默',0,19,743,88,0,0,DATE_SUB(NOW(),INTERVAL 1 DAY)
UNION ALL SELECT 104,'topic','general','你最近读到的一句很有启发的话是什么？','可以是书里的、文章里的，也可以是同事不经意间说的一句话，顺便聊聊它为什么打动了你。','可以是书里的、文章里的，也可以是同事不经意间说的一句话，顺便聊聊它为什么打动了你。','["日常","阅读"]',0,'苏晚',0,56,1094,63,0,0,DATE_SUB(NOW(),INTERVAL 1 DAY)
UNION ALL SELECT 105,'topic','design','信息密集型后台，怎样让页面不那么“拥挤”？','结合最近几个页面的改版，聊聊留白、视觉层级、表格密度和渐进展示之间的取舍。','结合最近几个页面的改版，聊聊留白、视觉层级、表格密度和渐进展示之间的取舍。','["UI","后台设计","体验"]',0,'许知夏',0,17,682,51,0,0,DATE_SUB(NOW(),INTERVAL 2 DAY)
UNION ALL SELECT 106,'topic','career','工作三年以后，我开始重新理解“打好基础”','基础并不是背概念，而是在陌生问题面前知道如何拆解、验证和找到可靠的信息。','基础并不是背概念，而是在陌生问题面前知道如何拆解、验证和找到可靠的信息。','["成长","学习方法"]',0,'唐可',0,31,932,104,0,0,DATE_SUB(NOW(),INTERVAL 2 DAY)
UNION ALL SELECT 107,'topic','frontend','前端监控除了报错，还应该关注哪些指标？','从真实用户感受到的慢、卡、白屏出发，整理一套适合中小型项目的监控指标。','从真实用户感受到的慢、卡、白屏出发，整理一套适合中小型项目的监控指标。','["前端监控","性能"]',0,'叶舟',0,15,598,47,0,0,DATE_SUB(NOW(),INTERVAL 3 DAY)
UNION ALL SELECT 108,'topic','backend','数据库索引不是越多越好：一次慢查询优化记录','通过执行计划和真实数据分布，定位联合索引顺序不合理的问题，优化前后对比也一并附上。','通过执行计划和真实数据分布，定位联合索引顺序不合理的问题，优化前后对比也一并附上。','["MySQL","索引","性能优化"]',0,'沈星',0,27,1105,91,0,1,DATE_SUB(NOW(),INTERVAL 4 DAY)
UNION ALL SELECT 201,'feed','general','','今天把一个反复出现的小问题整理成了检查清单。团队协作里真正省时间的，往往不是更复杂的工具，而是把那些容易遗漏的步骤认真记录下来。','今天把一个反复出现的小问题整理成了检查清单。团队协作里真正省时间的，往往不是更复杂的工具，而是把那些容易遗漏的步骤认真记录下来。','["日常","团队协作"]',0,'苏晚',0,12,328,46,0,0,DATE_SUB(NOW(),INTERVAL 1 HOUR)
UNION ALL SELECT 202,'feed','frontend','','重新整理了工作台，也给自己定了一个小目标：每天留半小时阅读优秀项目的源码，不追求数量，坚持把一个问题真正看懂。','重新整理了工作台，也给自己定了一个小目标：每天留半小时阅读优秀项目的源码，不追求数量，坚持把一个问题真正看懂。','["学习","前端"]',0,'叶舟',0,8,216,35,0,0,DATE_SUB(NOW(),INTERVAL 5 HOUR)
UNION ALL SELECT 301,'question','backend','Spring Boot 接口偶发超时，应该从哪些层面开始排查？','服务平均响应正常，但每天会出现少量超过 3 秒的请求。已经确认没有明显的慢 SQL，网关和应用超时应该如何配合排查？','服务平均响应正常，但每天会出现少量超过 3 秒的请求。已经确认没有明显的慢 SQL，网关和应用超时应该如何配合排查？','["Spring Boot","超时","问题排查"]',0,'顾言',100,16,642,28,0,0,DATE_SUB(NOW(),INTERVAL 2 HOUR)
UNION ALL SELECT 302,'question','frontend','Vue 3 复杂表单应该如何拆分，才能减少联动校验的混乱？','当前表单包含多个动态分组，字段之间存在跨组件校验。希望了解大家在组件通信、草稿保存和统一提交方面的实践。','当前表单包含多个动态分组，字段之间存在跨组件校验。希望了解大家在组件通信、草稿保存和统一提交方面的实践。','["Vue 3","表单","组件设计"]',0,'周清妍',50,21,784,39,0,1,DATE_SUB(NOW(),INTERVAL 1 DAY)
UNION ALL SELECT 401,'article','devops','从零建立可靠的轻量发布流程：构建、检查与回滚','不依赖复杂平台，如何通过一份清晰的发布清单和基础自动化，让每一次上线都可检查、可追踪、可回滚。\n\n先把每次发布拆成可验证的步骤，再让工具自动执行这些步骤。','不依赖复杂平台，如何通过一份清晰的发布清单和基础自动化，让每一次上线都可检查、可追踪、可回滚。','["DevOps","发布","最佳实践"]',0,'陈默',0,32,1680,126,0,0,DATE_SUB(NOW(),INTERVAL 3 DAY)
UNION ALL SELECT 402,'article','design','信息密度与阅读效率：社区产品中的留白设计','从列表、正文到任务中心，分析字体层级、间距和辅助信息如何共同影响阅读效率，并给出一套可复用的设计原则。','从列表、正文到任务中心，分析字体层级、间距和辅助信息如何共同影响阅读效率，并给出一套可复用的设计原则。','["设计","阅读体验","社区"]',0,'许知夏',0,18,1260,98,0,0,DATE_SUB(NOW(),INTERVAL 5 DAY)
) seed
WHERE NOT EXISTS (SELECT 1 FROM forum_thread thread WHERE thread.kind=seed.kind AND thread.board_key=seed.board_key AND thread.title=seed.title AND thread.content=seed.content)
AND NOT EXISTS (SELECT 1 FROM forum_id_migration migration WHERE migration.table_name='forum_thread' AND migration.old_id=seed.id);

INSERT INTO forum_comment (id,thread_id,author_account_id,author_name,content,likes_count,created_at)
SELECT seed.id,COALESCE((SELECT migration.new_id FROM forum_id_migration migration WHERE migration.table_name='forum_thread' AND migration.old_id=seed.thread_id),seed.thread_id),seed.author_account_id,seed.author_name,seed.content,seed.likes_count,seed.created_at FROM (
SELECT 1 AS id,101 AS thread_id,0 AS author_account_id,'顾言' AS author_name,'“重试次数要和超时时间一起设计”这一点很有共鸣。之前我们也遇到过上游已经超时，下游还在重复执行的情况。' AS content,18 AS likes_count,DATE_SUB(NOW(),INTERVAL 90 MINUTE) AS created_at
UNION ALL SELECT 2,101,0,'程一','建议再补充一下调用链采样率。排查期间可以临时提高采样，但全量打开在高并发场景下也会带来明显开销。',12,DATE_SUB(NOW(),INTERVAL 60 MINUTE)
UNION ALL SELECT 3,101,0,'林宇辰','感谢补充，采样率确实是容易忽略的一点。我们已经把动态调整采样率加入了后续优化项。',8,DATE_SUB(NOW(),INTERVAL 30 MINUTE)
) seed
JOIN forum_thread thread ON thread.id=COALESCE((SELECT migration.new_id FROM forum_id_migration migration WHERE migration.table_name='forum_thread' AND migration.old_id=seed.thread_id),seed.thread_id)
WHERE NOT EXISTS (SELECT 1 FROM forum_comment comment WHERE comment.thread_id=thread.id AND comment.author_account_id=seed.author_account_id AND comment.author_name=seed.author_name AND comment.content=seed.content)
AND NOT EXISTS (SELECT 1 FROM forum_id_migration migration WHERE migration.table_name='forum_comment' AND migration.old_id=seed.id);

INSERT INTO forum_gift (id,name,category,description,points,stock,icon)
SELECT seed.id,seed.name,seed.category,seed.description,seed.points,seed.stock,seed.icon FROM (
SELECT 1 AS id,'Polaris 定制马克杯' AS name,'周边' AS category,'简约陶瓷马克杯，社区限定配色' AS description,800 AS points,20 AS stock,'coffee' AS icon
UNION ALL SELECT 2,'机械键盘键帽套装','数码','PBT 热升华键帽，兼容常见配列',1500,8,'keyboard'
UNION ALL SELECT 3,'技术图书兑换券','学习','可兑换指定技术图书一本',1200,15,'book'
UNION ALL SELECT 4,'Polaris 帆布包','周边','加厚帆布材质，日常通勤适用',600,30,'bag'
UNION ALL SELECT 5,'桌面理线套装','数码','桌面线缆整理与收纳组合',300,50,'tool'
UNION ALL SELECT 6,'在线课程优惠券','学习','指定在线课程 100 元优惠券',1000,10,'video'
) seed
WHERE NOT EXISTS (SELECT 1 FROM forum_gift gift WHERE gift.name=seed.name)
AND NOT EXISTS (SELECT 1 FROM forum_id_migration migration WHERE migration.table_name='forum_gift' AND migration.old_id=seed.id);

INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 1,'赌博',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='赌博')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=1);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2,'诈骗',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='诈骗')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 3,'色情',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='色情')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=3);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 4,'毒品',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='毒品')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=4);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 5,'枪支',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='枪支')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=5);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 6,'刷单',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='刷单')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=6);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 7,'传销',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='传销')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=7);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 8,'恶意广告',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='恶意广告')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=8);
