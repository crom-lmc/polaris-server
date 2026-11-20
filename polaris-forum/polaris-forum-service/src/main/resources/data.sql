INSERT INTO forum_thread (id,kind,board_key,title,content,excerpt,tags,author_account_id,author_name,reward,replies_count,views_count,likes_count,pinned,solved,created_at)
SELECT seed.* FROM (
SELECT 102 AS id,'topic' AS kind,'frontend' AS board_key,'Vue 3 大型表单拆分的几种实践，你更喜欢哪一种？' AS title,'从组件通信、校验时机和草稿恢复三个角度，对比 provide/inject、组合式函数与集中状态管理的实际体验。欢迎分享你在复杂表单中的拆分方式。' AS content,'从组件通信、校验时机和草稿恢复三个角度，对比不同组件通信方案的实际体验。' AS excerpt,'["Vue 3","TypeScript","工程化"]' AS tags,0 AS author_account_id,'周清妍' AS author_name,0 AS reward,24 AS replies_count,856 AS views_count,72 AS likes_count,0 AS pinned,0 AS solved,DATE_SUB(NOW(),INTERVAL 3 HOUR) AS created_at
UNION ALL SELECT 103,'topic','devops','分享一份轻量 Docker 发布检查清单','不追求复杂平台，只用 Compose 和 Nginx 也能把构建、发布、健康检查与回滚做得清晰可靠。','不追求复杂平台，只用 Compose 和 Nginx 也能把构建、发布、健康检查与回滚做得清晰可靠。','["Docker","Nginx","发布"]',0,'陈默',0,19,743,88,0,0,DATE_SUB(NOW(),INTERVAL 1 DAY)
UNION ALL SELECT 104,'topic','general','你最近读到的一句很有启发的话是什么？','可以是书里的、文章里的，也可以是同事不经意间说的一句话，顺便聊聊它为什么打动了你。','可以是书里的、文章里的，也可以是同事不经意间说的一句话，顺便聊聊它为什么打动了你。','["日常","阅读"]',0,'苏晚',0,56,1094,63,0,0,DATE_SUB(NOW(),INTERVAL 1 DAY)
UNION ALL SELECT 105,'topic','design','信息密集型后台，怎样让页面不那么“拥挤”？','结合最近几个页面的改版，聊聊留白、视觉层级、表格密度和渐进展示之间的取舍。','结合最近几个页面的改版，聊聊留白、视觉层级、表格密度和渐进展示之间的取舍。','["UI","后台设计","体验"]',0,'许知夏',0,17,682,51,0,0,DATE_SUB(NOW(),INTERVAL 2 DAY)
UNION ALL SELECT 106,'topic','career','工作三年以后，我开始重新理解“打好基础”','基础并不是背概念，而是在陌生问题面前知道如何拆解、验证和找到可靠的信息。','基础并不是背概念，而是在陌生问题面前知道如何拆解、验证和找到可靠的信息。','["成长","学习方法"]',0,'唐可',0,31,932,104,0,0,DATE_SUB(NOW(),INTERVAL 2 DAY)
UNION ALL SELECT 107,'topic','frontend','前端监控除了报错，还应该关注哪些指标？','从真实用户感受到的慢、卡、白屏出发，整理一套适合中小型项目的监控指标。','从真实用户感受到的慢、卡、白屏出发，整理一套适合中小型项目的监控指标。','["前端监控","性能"]',0,'叶舟',0,15,598,47,0,0,DATE_SUB(NOW(),INTERVAL 3 DAY)
UNION ALL SELECT 201,'feed','general','','今天把一个反复出现的小问题整理成了检查清单。团队协作里真正省时间的，往往不是更复杂的工具，而是把那些容易遗漏的步骤认真记录下来。','今天把一个反复出现的小问题整理成了检查清单。团队协作里真正省时间的，往往不是更复杂的工具，而是把那些容易遗漏的步骤认真记录下来。','["日常","团队协作"]',0,'苏晚',0,12,328,46,0,0,DATE_SUB(NOW(),INTERVAL 1 HOUR)
UNION ALL SELECT 202,'feed','frontend','','重新整理了工作台，也给自己定了一个小目标：每天留半小时阅读优秀项目的源码，不追求数量，坚持把一个问题真正看懂。','重新整理了工作台，也给自己定了一个小目标：每天留半小时阅读优秀项目的源码，不追求数量，坚持把一个问题真正看懂。','["学习","前端"]',0,'叶舟',0,8,216,35,0,0,DATE_SUB(NOW(),INTERVAL 5 HOUR)
UNION ALL SELECT 302,'question','frontend','Vue 3 复杂表单应该如何拆分，才能减少联动校验的混乱？','当前表单包含多个动态分组，字段之间存在跨组件校验。希望了解大家在组件通信、草稿保存和统一提交方面的实践。','当前表单包含多个动态分组，字段之间存在跨组件校验。希望了解大家在组件通信、草稿保存和统一提交方面的实践。','["Vue 3","表单","组件设计"]',0,'周清妍',50,21,784,39,0,1,DATE_SUB(NOW(),INTERVAL 1 DAY)
UNION ALL SELECT 401,'article','devops','从零建立可靠的轻量发布流程：构建、检查与回滚','不依赖复杂平台，如何通过一份清晰的发布清单和基础自动化，让每一次上线都可检查、可追踪、可回滚。\n\n先把每次发布拆成可验证的步骤，再让工具自动执行这些步骤。','不依赖复杂平台，如何通过一份清晰的发布清单和基础自动化，让每一次上线都可检查、可追踪、可回滚。','["DevOps","发布","最佳实践"]',0,'陈默',0,32,1680,126,0,0,DATE_SUB(NOW(),INTERVAL 3 DAY)
UNION ALL SELECT 402,'article','design','信息密度与阅读效率：社区产品中的留白设计','从列表、正文到任务中心，分析字体层级、间距和辅助信息如何共同影响阅读效率，并给出一套可复用的设计原则。','从列表、正文到任务中心，分析字体层级、间距和辅助信息如何共同影响阅读效率，并给出一套可复用的设计原则。','["设计","阅读体验","社区"]',0,'许知夏',0,18,1260,98,0,0,DATE_SUB(NOW(),INTERVAL 5 DAY)
) seed
WHERE NOT EXISTS (SELECT 1 FROM forum_thread thread WHERE thread.kind=seed.kind AND thread.board_key=seed.board_key AND thread.title=seed.title AND thread.content=seed.content)
AND NOT EXISTS (SELECT 1 FROM forum_id_migration migration WHERE migration.table_name='forum_thread' AND migration.old_id=seed.id);

INSERT INTO forum_gift (id,name,category,description,points,stock,icon,min_level)
SELECT seed.id,seed.name,seed.category,seed.description,seed.points,seed.stock,seed.icon,seed.min_level FROM (
SELECT 1 AS id,'Polaris 定制马克杯' AS name,'周边' AS category,'简约陶瓷马克杯，社区限定配色' AS description,800 AS points,20 AS stock,'coffee' AS icon,1 AS min_level
UNION ALL SELECT 2,'机械键盘键帽套装','数码','PBT 热升华键帽，兼容常见配列',1500,8,'keyboard',1
UNION ALL SELECT 3,'技术图书兑换券','学习','可兑换指定技术图书一本',1200,15,'book',1
UNION ALL SELECT 4,'Polaris 帆布包','周边','加厚帆布材质，日常通勤适用',600,30,'bag',1
UNION ALL SELECT 5,'桌面理线套装','数码','桌面线缆整理与收纳组合',300,50,'tool',1
UNION ALL SELECT 6,'在线课程优惠券','学习','指定在线课程 100 元优惠券',1000,10,'video',1
UNION ALL SELECT 7,'工作日午餐券','餐饮','满 30 元可用的工作日午餐券',400,60,'meal',1
UNION ALL SELECT 8,'精品咖啡券','餐饮','社区专属精品咖啡兑换券',200,100,'coffee',1
UNION ALL SELECT 9,'电影票兑换券','生活','全国通兑电影票一张',900,25,'movie',2
UNION ALL SELECT 10,'视频会员月卡','会员','主流视频平台会员月卡',1200,30,'video',3
UNION ALL SELECT 11,'Polaris AI Token 100万','AI服务','Polaris AI 大模型 100 万 Token 额度',1800,40,'ai',5
UNION ALL SELECT 12,'音乐会员季卡','会员','主流音乐平台会员季卡',2600,20,'music',6
UNION ALL SELECT 13,'Polaris AI Token 500万','AI服务','Polaris AI 大模型 500 万 Token 额度',6000,15,'ai',10
UNION ALL SELECT 14,'云盘会员年卡','会员','主流云盘会员年卡',3600,15,'cloud',8
UNION ALL SELECT 15,'技术大会门票','学习','年度技术大会线上门票一张',8000,5,'ticket',15
UNION ALL SELECT 16,'北极星限定礼盒','周边','社区最高荣誉限定周边礼盒',12000,3,'box',20
) seed
WHERE NOT EXISTS (SELECT 1 FROM forum_gift gift WHERE gift.name=seed.name)
AND NOT EXISTS (SELECT 1 FROM forum_id_migration migration WHERE migration.table_name='forum_gift' AND migration.old_id=seed.id);

-- 礼品展示图：幂等回填，已存在的礼品也会补上图片地址（站内 SVG，位于论坛前台 public/gifts）
UPDATE forum_gift SET image_url='/gifts/mug.svg' WHERE name='Polaris 定制马克杯' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/keyboard.svg' WHERE name='机械键盘键帽套装' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/book.svg' WHERE name='技术图书兑换券' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/bag.svg' WHERE name='Polaris 帆布包' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/tool.svg' WHERE name='桌面理线套装' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/course.svg' WHERE name='在线课程优惠券' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/meal.svg' WHERE name='工作日午餐券' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/coffee.svg' WHERE name='精品咖啡券' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/movie.svg' WHERE name='电影票兑换券' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/video.svg' WHERE name='视频会员月卡' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/ai.svg' WHERE name='Polaris AI Token 100万' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/music.svg' WHERE name='音乐会员季卡' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/ai.svg' WHERE name='Polaris AI Token 500万' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/cloud.svg' WHERE name='云盘会员年卡' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/ticket.svg' WHERE name='技术大会门票' AND (image_url IS NULL OR image_url='');
UPDATE forum_gift SET image_url='/gifts/box.svg' WHERE name='北极星限定礼盒' AND (image_url IS NULL OR image_url='');

-- 老礼品统一补齐等级门槛为 1（幂等）
UPDATE forum_gift SET min_level=1 WHERE min_level IS NULL OR min_level<1;

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


-- 扩展敏感词词库（幂等种子）
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201001,'淫秽',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='淫秽')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201001);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201002,'黄色网站',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='黄色网站')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201002);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201003,'成人影片',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='成人影片')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201003);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201004,'av女优',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='av女优')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201004);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201005,'裸照',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='裸照')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201005);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201006,'裸体',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='裸体')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201006);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201007,'全裸',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='全裸')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201007);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201008,'做爱',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='做爱')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201008);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201009,'性交',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='性交')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201009);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201010,'自慰',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='自慰')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201010);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201011,'口交',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='口交')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201011);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201012,'肛交',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='肛交')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201012);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201013,'性虐',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='性虐')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201013);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201014,'卖淫',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='卖淫')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201014);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201015,'嫖娼',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='嫖娼')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201015);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201016,'援交',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='援交')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201016);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201017,'找小姐',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='找小姐')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201017);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201018,'一夜情',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='一夜情')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201018);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201019,'成人内容',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='成人内容')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201019);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201020,'黄片',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='黄片')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201020);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201021,'偷拍',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='偷拍')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201021);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201022,'走光',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='走光')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201022);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201023,'赌场',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='赌场')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201023);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201024,'博彩',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='博彩')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201024);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201025,'下注',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='下注')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201025);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201026,'庄家',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='庄家')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201026);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201027,'赔率',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='赔率')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201027);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201028,'私彩',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='私彩')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201028);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201029,'六合彩',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='六合彩')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201029);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201030,'百家乐',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='百家乐')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201030);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201031,'德州扑克',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='德州扑克')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201031);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201032,'老虎机',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='老虎机')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201032);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201033,'赌资',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='赌资')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201033);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201034,'网赌',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='网赌')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201034);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201035,'时时彩',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='时时彩')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201035);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201036,'龙虎斗',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='龙虎斗')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201036);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201037,'博彩网站',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='博彩网站')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201037);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201038,'大麻',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='大麻')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201038);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201039,'冰毒',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='冰毒')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201039);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201040,'海洛因',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='海洛因')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201040);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201041,'摇头丸',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='摇头丸')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201041);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201042,'k粉',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='k粉')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201042);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201043,'吸毒',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='吸毒')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201043);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201044,'贩毒',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='贩毒')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201044);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201045,'制毒',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='制毒')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201045);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201046,'违禁品',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='违禁品')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201046);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201047,'弹药',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='弹药')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201047);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201048,'军火',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='军火')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201048);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201049,'管制刀具',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='管制刀具')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201049);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201050,'爆炸物',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='爆炸物')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201050);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201051,'迷药',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='迷药')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201051);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201052,'AK47',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='AK47')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201052);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201053,'弓弩',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='弓弩')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201053);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201054,'骗子',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='骗子')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201054);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201055,'杀猪盘',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='杀猪盘')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201055);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201056,'套路贷',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='套路贷')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201056);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201057,'高利贷',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='高利贷')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201057);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201058,'裸贷',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='裸贷')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201058);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201059,'钓鱼网站',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='钓鱼网站')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201059);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201060,'虚假投资',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='虚假投资')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201060);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201061,'资金盘',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='资金盘')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201061);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201062,'庞氏骗局',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='庞氏骗局')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201062);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201063,'洗钱',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='洗钱')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201063);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201064,'跑分',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='跑分')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201064);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201065,'两卡',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='两卡')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201065);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201066,'买卖账户',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='买卖账户')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201066);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201067,'代收货款骗局',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='代收货款骗局')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201067);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201068,'杀熟',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='杀熟')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201068);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201069,'人肉搜索',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='人肉搜索')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201069);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201070,'开盒',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='开盒')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201070);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201071,'盗号',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='盗号')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201071);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201072,'撞库',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='撞库')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201072);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201073,'外挂',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='外挂')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201073);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201074,'破解版',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='破解版')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201074);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201075,'盗版软件',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='盗版软件')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201075);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201076,'翻墙软件',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='翻墙软件')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201076);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201077,'非法集资',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='非法集资')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201077);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201078,'傻逼',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='傻逼')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201078);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201079,'煞笔',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='煞笔')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201079);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201080,'草泥马',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='草泥马')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201080);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201081,'尼玛',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='尼玛')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201081);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201082,'妈的',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='妈的')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201082);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201083,'去死',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='去死')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201083);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201084,'贱人',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='贱人')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201084);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201085,'废物',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='废物')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201085);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201086,'脑残',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='脑残')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201086);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201087,'白痴',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='白痴')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201087);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201088,'弱智',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='弱智')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201088);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201089,'狗东西',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='狗东西')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201089);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201090,'杂种',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='杂种')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201090);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201091,'滚蛋',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='滚蛋')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201091);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201092,'蠢货',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='蠢货')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201092);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201093,'加微信',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='加微信')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201093);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201094,'加V信',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='加V信')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201094);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201095,'扫码领',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='扫码领')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201095);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201096,'点击领取',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='点击领取')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201096);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201097,'免费领',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='免费领')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201097);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201098,'刷粉',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='刷粉')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201098);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201099,'刷赞',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='刷赞')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201099);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201100,'代刷',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='代刷')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201100);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201101,'营销号',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='营销号')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201101);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201102,'垃圾营销',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='垃圾营销')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201102);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201103,'兼职刷单',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='兼职刷单')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201103);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201104,'日赚斗金',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='日赚斗金')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201104);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201105,'躺赚',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='躺赚')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201105);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201106,'返利骗局',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='返利骗局')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201106);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201107,'代孕',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='代孕')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201107);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201108,'偷拍视频',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='偷拍视频')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201108);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201109,'黑料',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='黑料')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201109);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201110,'山寨货',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='山寨货')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201110);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201111,'高仿货',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='高仿货')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201111);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201112,'假证',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='假证')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201112);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201113,'办假证',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='办假证')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201113);
INSERT INTO forum_sensitive_word (id,word,enabled)
SELECT 2123219848727201114,'刻章办证',1 WHERE NOT EXISTS (SELECT 1 FROM forum_sensitive_word WHERE word='刻章办证')
AND NOT EXISTS (SELECT 1 FROM forum_id_migration WHERE table_name='forum_sensitive_word' AND old_id=2123219848727201114);

-- ===== 敏感词扩充：11 类共 486 个（INSERT IGNORE + word 唯一键保证幂等；ID 段 ...202001 起）=====
-- 分类：赌博博彩、诈骗、色情低俗、辱骂人身攻击、办证造假、毒品违禁、武器爆炸违禁、广告引流营销、隐私入侵网络攻击、暴力恐怖违法、低俗不良
INSERT IGNORE INTO forum_sensitive_word (id,word,enabled) VALUES
(2123219848727202001,'博彩平台',1),
(2123219848727202002,'网络博彩',1),
(2123219848727202003,'在线博彩',1),
(2123219848727202004,'博彩游戏',1),
(2123219848727202005,'赌博网',1),
(2123219848727202006,'赌博平台',1),
(2123219848727202007,'网上赌场',1),
(2123219848727202008,'线上赌场',1),
(2123219848727202009,'真人赌场',1),
(2123219848727202010,'真人娱乐场',1),
(2123219848727202011,'赌球',1),
(2123219848727202012,'赌马',1),
(2123219848727202013,'外围赌球',1),
(2123219848727202014,'盘口',1),
(2123219848727202015,'让球盘',1),
(2123219848727202016,'大小球',1),
(2123219848727202017,'走地盘',1),
(2123219848727202018,'水位',1),
(2123219848727202019,'返水',1),
(2123219848727202020,'抽水',1),
(2123219848727202021,'洗码',1),
(2123219848727202022,'返佣',1),
(2123219848727202023,'招代理',1),
(2123219848727202024,'龙虎',1),
(2123219848727202025,'炸金花',1),
(2123219848727202026,'三公',1),
(2123219848727202027,'牛牛',1),
(2123219848727202028,'梭哈',1),
(2123219848727202029,'二十一点',1),
(2123219848727202030,'轮盘',1),
(2123219848727202031,'骰宝',1),
(2123219848727202032,'时时彩平台',1),
(2123219848727202033,'分分彩',1),
(2123219848727202034,'三分彩',1),
(2123219848727202035,'五分彩',1),
(2123219848727202036,'快三',1),
(2123219848727202037,'幸运飞艇',1),
(2123219848727202038,'北京赛车',1),
(2123219848727202039,'PK10',1),
(2123219848727202040,'彩票包赚',1),
(2123219848727202041,'内幕消息',1),
(2123219848727202042,'必中',1),
(2123219848727202043,'稳赚',1),
(2123219848727202044,'回血',1),
(2123219848727202045,'上岸',1),
(2123219848727202046,'戒赌',1),
(2123219848727202047,'赌债',1),
(2123219848727202048,'网投',1),
(2123219848727202049,'投注平台',1),
(2123219848727202050,'下注平台',1),
(2123219848727202051,'博彩代理',1),
(2123219848727202052,'稳赚不赔',1),
(2123219848727202053,'德州',1),
(2123219848727202054,'百家乐技巧',1),
(2123219848727202055,'彩票网站',1),
(2123219848727202056,'投注网站',1),
(2123219848727202057,'私彩网站',1),
(2123219848727202058,'网络赌博',1),
(2123219848727202059,'境外赌博',1),
(2123219848727202060,'电信诈骗',1),
(2123219848727202061,'网络诈骗',1),
(2123219848727202062,'刷单返利',1),
(2123219848727202063,'刷单兼职',1),
(2123219848727202064,'返利诈骗',1),
(2123219848727202065,'冒充客服',1),
(2123219848727202066,'冒充公检法',1),
(2123219848727202067,'冒充领导',1),
(2123219848727202068,'冒充熟人',1),
(2123219848727202069,'冒充银行',1),
(2123219848727202070,'冒充快递',1),
(2123219848727202071,'中奖诈骗',1),
(2123219848727202072,'虚假中奖',1),
(2123219848727202073,'领取奖品',1),
(2123219848727202074,'红包骗局',1),
(2123219848727202075,'福利骗局',1),
(2123219848727202076,'补贴骗局',1),
(2123219848727202077,'退税骗局',1),
(2123219848727202078,'退款骗局',1),
(2123219848727202079,'理赔骗局',1),
(2123219848727202080,'低价诱惑',1),
(2123219848727202081,'超低价',1),
(2123219848727202082,'钓鱼链接',1),
(2123219848727202083,'钓鱼短信',1),
(2123219848727202084,'木马链接',1),
(2123219848727202085,'病毒链接',1),
(2123219848727202086,'虚假客服',1),
(2123219848727202087,'假客服',1),
(2123219848727202088,'裸聊诈骗',1),
(2123219848727202089,'裸聊',1),
(2123219848727202090,'杀鸟盘',1),
(2123219848727202091,'杀鱼盘',1),
(2123219848727202092,'情感诈骗',1),
(2123219848727202093,'恋爱诈骗',1),
(2123219848727202094,'网恋诈骗',1),
(2123219848727202095,'投资诈骗',1),
(2123219848727202096,'理财诈骗',1),
(2123219848727202097,'荐股',1),
(2123219848727202098,'带单',1),
(2123219848727202099,'喊单',1),
(2123219848727202100,'老师带单',1),
(2123219848727202101,'外汇骗局',1),
(2123219848727202102,'期货骗局',1),
(2123219848727202103,'虚拟币骗局',1),
(2123219848727202104,'空气币',1),
(2123219848727202105,'山寨币',1),
(2123219848727202106,'割韭菜',1),
(2123219848727202107,'拆分盘',1),
(2123219848727202108,'互助盘',1),
(2123219848727202109,'消费全返',1),
(2123219848727202110,'返利平台',1),
(2123219848727202111,'连赚',1),
(2123219848727202112,'高收益',1),
(2123219848727202113,'保本保息',1),
(2123219848727202114,'快速致富',1),
(2123219848727202115,'一夜暴富',1),
(2123219848727202116,'暴富项目',1),
(2123219848727202117,'拉人头',1),
(2123219848727202118,'发展下线',1),
(2123219848727202119,'层级返利',1),
(2123219848727202120,'团队计酬',1),
(2123219848727202121,'入门费',1),
(2123219848727202122,'网络营销',1),
(2123219848727202123,'资本运作',1),
(2123219848727202124,'人际网络',1),
(2123219848727202125,'老鼠会',1),
(2123219848727202126,'非法吸收公众存款',1),
(2123219848727202127,'集资诈骗',1),
(2123219848727202128,'砍头息',1),
(2123219848727202129,'阴阳合同',1),
(2123219848727202130,'空白合同',1),
(2123219848727202131,'暴力催收',1),
(2123219848727202132,'通讯录轰炸',1),
(2123219848727202133,'呼死你',1),
(2123219848727202134,'敲诈勒索',1),
(2123219848727202135,'恐吓',1),
(2123219848727202136,'勒索',1),
(2123219848727202137,'照片门',1),
(2123219848727202138,'艳照门',1),
(2123219848727202139,'幸运彩票',1),
(2123219848727202140,'免费福利',1),
(2123219848727202141,'点击领取奖金',1),
(2123219848727202142,'成人',1),
(2123219848727202143,'成人网站',1),
(2123219848727202144,'成人论坛',1),
(2123219848727202145,'成人直播',1),
(2123219848727202146,'成人用品',1),
(2123219848727202147,'性爱',1),
(2123219848727202148,'性行为',1),
(2123219848727202149,'情趣用品',1),
(2123219848727202150,'春药',1),
(2123219848727202151,'迷奸',1),
(2123219848727202152,'催情',1),
(2123219848727202153,'淫秽物品',1),
(2123219848727202154,'淫秽视频',1),
(2123219848727202155,'淫秽图片',1),
(2123219848727202156,'黄色录像',1),
(2123219848727202157,'黄色小说',1),
(2123219848727202158,'黄色图片',1),
(2123219848727202159,'色情网站',1),
(2123219848727202160,'色情视频',1),
(2123219848727202161,'色情图片',1),
(2123219848727202162,'色情小说',1),
(2123219848727202163,'色情直播',1),
(2123219848727202164,'色情服务',1),
(2123219848727202165,'色情交易',1),
(2123219848727202166,'涉黄',1),
(2123219848727202167,'扫黄',1),
(2123219848727202168,'卖淫嫖娼',1),
(2123219848727202169,'性交易',1),
(2123219848727202170,'性服务',1),
(2123219848727202171,'提供性服务',1),
(2123219848727202172,'上门服务',1),
(2123219848727202173,'全套服务',1),
(2123219848727202174,'特殊服务',1),
(2123219848727202175,'一条龙服务',1),
(2123219848727202176,'楼凤',1),
(2123219848727202177,'站街',1),
(2123219848727202178,'红灯区',1),
(2123219848727202179,'成人娱乐',1),
(2123219848727202180,'陪酒',1),
(2123219848727202181,'陪侍',1),
(2123219848727202182,'大尺度',1),
(2123219848727202183,'限制级',1),
(2123219848727202184,'R18',1),
(2123219848727202185,'18禁',1),
(2123219848727202186,'福利图',1),
(2123219848727202187,'福利视频',1),
(2123219848727202188,'涩图',1),
(2123219848727202189,'本子',1),
(2123219848727202190,'里番',1),
(2123219848727202191,'约炮',1),
(2123219848727202192,'炮友',1),
(2123219848727202193,'同城交友',1),
(2123219848727202194,'求包养',1),
(2123219848727202195,'包养',1),
(2123219848727202196,'二奶',1),
(2123219848727202197,'小三',1),
(2123219848727202198,'情妇',1),
(2123219848727202199,'桑拿',1),
(2123219848727202200,'洗浴中心',1),
(2123219848727202201,'夜总会',1),
(2123219848727202202,'陪酒女',1),
(2123219848727202203,'少爷公主',1),
(2123219848727202204,'你妈',1),
(2123219848727202205,'妈卖批',1),
(2123219848727202206,'妈卖屁',1),
(2123219848727202207,'卧槽',1),
(2123219848727202208,'我操',1),
(2123219848727202209,'特么',1),
(2123219848727202210,'他妈的',1),
(2123219848727202211,'你大爷',1),
(2123219848727202212,'狗日的',1),
(2123219848727202213,'狗杂种',1),
(2123219848727202214,'狗娘养',1),
(2123219848727202215,'畜生',1),
(2123219848727202216,'畜牲',1),
(2123219848727202217,'人渣',1),
(2123219848727202218,'败类',1),
(2123219848727202219,'混蛋',1),
(2123219848727202220,'王八蛋',1),
(2123219848727202221,'乌龟王八',1),
(2123219848727202222,'贱货',1),
(2123219848727202223,'骚货',1),
(2123219848727202224,'婊子',1),
(2123219848727202225,'绿茶婊',1),
(2123219848727202226,'心机婊',1),
(2123219848727202227,'母狗',1),
(2123219848727202228,'臭不要脸',1),
(2123219848727202229,'不要脸',1),
(2123219848727202230,'无耻',1),
(2123219848727202231,'下流',1),
(2123219848727202232,'卑鄙',1),
(2123219848727202233,'龌龊',1),
(2123219848727202234,'恶心',1),
(2123219848727202235,'变态',1),
(2123219848727202236,'神经病',1),
(2123219848727202237,'精神病',1),
(2123219848727202238,'有病',1),
(2123219848727202239,'脑瘫',1),
(2123219848727202240,'智障',1),
(2123219848727202241,'低能',1),
(2123219848727202242,'傻叉',1),
(2123219848727202243,'沙雕',1),
(2123219848727202244,'二逼',1),
(2123219848727202245,'二货',1),
(2123219848727202246,'蠢猪',1),
(2123219848727202247,'蠢驴',1),
(2123219848727202248,'笨猪',1),
(2123219848727202249,'死胖子',1),
(2123219848727202250,'肥猪',1),
(2123219848727202251,'老不死',1),
(2123219848727202252,'老妖婆',1),
(2123219848727202253,'丑八怪',1),
(2123219848727202254,'恐龙',1),
(2123219848727202255,'滚粗',1),
(2123219848727202256,'滚远点',1),
(2123219848727202257,'有多远滚多远',1),
(2123219848727202258,'死一户口本',1),
(2123219848727202259,'死全家',1),
(2123219848727202260,'全家死光',1),
(2123219848727202261,'不得好死',1),
(2123219848727202262,'天杀的',1),
(2123219848727202263,'挨千刀',1),
(2123219848727202264,'千刀万剐',1),
(2123219848727202265,'活该',1),
(2123219848727202266,'报应',1),
(2123219848727202267,'制假证',1),
(2123219848727202268,'制作假证',1),
(2123219848727202269,'伪造证件',1),
(2123219848727202270,'伪造公章',1),
(2123219848727202271,'私刻公章',1),
(2123219848727202272,'假公章',1),
(2123219848727202273,'假印章',1),
(2123219848727202274,'假文凭',1),
(2123219848727202275,'假学历',1),
(2123219848727202276,'假毕业证',1),
(2123219848727202277,'假学位证',1),
(2123219848727202278,'假身份证',1),
(2123219848727202279,'假护照',1),
(2123219848727202280,'假驾照',1),
(2123219848727202281,'假行驶证',1),
(2123219848727202282,'假车牌',1),
(2123219848727202283,'假发票',1),
(2123219848727202284,'虚开发票',1),
(2123219848727202285,'代开发票',1),
(2123219848727202286,'假合同',1),
(2123219848727202287,'假证明',1),
(2123219848727202288,'假报告',1),
(2123219848727202289,'体检造假',1),
(2123219848727202290,'代体检',1),
(2123219848727202291,'代考',1),
(2123219848727202292,'替考',1),
(2123219848727202293,'枪手',1),
(2123219848727202294,'考试作弊',1),
(2123219848727202295,'作弊器',1),
(2123219848727202296,'舞弊',1),
(2123219848727202297,'论文代写',1),
(2123219848727202298,'代写论文',1),
(2123219848727202299,'代发论文',1),
(2123219848727202300,'买卖论文',1),
(2123219848727202301,'枪手代写',1),
(2123219848727202302,'高分包过',1),
(2123219848727202303,'包过',1),
(2123219848727202304,'保过',1),
(2123219848727202305,'内部资料',1),
(2123219848727202306,'泄题',1),
(2123219848727202307,'真题泄露',1),
(2123219848727202308,'运毒',1),
(2123219848727202309,'藏毒',1),
(2123219848727202310,'新型毒品',1),
(2123219848727202311,'合成毒品',1),
(2123219848727202312,'可卡因',1),
(2123219848727202313,'鸦片',1),
(2123219848727202314,'吗啡',1),
(2123219848727202315,'杜冷丁',1),
(2123219848727202316,'美沙酮',1),
(2123219848727202317,'LSD',1),
(2123219848727202318,'致幻剂',1),
(2123219848727202319,'迷幻药',1),
(2123219848727202320,'兴奋剂',1),
(2123219848727202321,'精神药品',1),
(2123219848727202322,'麻醉药品',1),
(2123219848727202323,'管制药品',1),
(2123219848727202324,'处方药滥用',1),
(2123219848727202325,'药品滥用',1),
(2123219848727202326,'上头',1),
(2123219848727202327,'飞叶子',1),
(2123219848727202328,'溜冰',1),
(2123219848727202329,'嗑药',1),
(2123219848727202330,'药瘾',1),
(2123219848727202331,'毒瘾',1),
(2123219848727202332,'戒毒',1),
(2123219848727202333,'毒贩',1),
(2123219848727202334,'毒枭',1),
(2123219848727202335,'毒品交易',1),
(2123219848727202336,'毒品走私',1),
(2123219848727202337,'国际贩毒',1),
(2123219848727202338,'运毒通道',1),
(2123219848727202339,'吸毒工具',1),
(2123219848727202340,'仿真枪',1),
(2123219848727202341,'气枪',1),
(2123219848727202342,'猎枪',1),
(2123219848727202343,'气手枪',1),
(2123219848727202344,'射钉枪',1),
(2123219848727202345,'改装枪',1),
(2123219848727202346,'私藏枪支',1),
(2123219848727202347,'非法持枪',1),
(2123219848727202348,'买卖枪支',1),
(2123219848727202349,'子弹',1),
(2123219848727202350,'实弹',1),
(2123219848727202351,'火药',1),
(2123219848727202352,'雷管',1),
(2123219848727202353,'炸药',1),
(2123219848727202354,'爆炸装置',1),
(2123219848727202355,'自制炸弹',1),
(2123219848727202356,'土炸弹',1),
(2123219848727202357,'汽油弹',1),
(2123219848727202358,'燃烧瓶',1),
(2123219848727202359,'弹簧刀',1),
(2123219848727202360,'跳刀',1),
(2123219848727202361,'三棱刮刀',1),
(2123219848727202362,'匕首',1),
(2123219848727202363,'砍刀',1),
(2123219848727202364,'双节棍',1),
(2123219848727202365,'甩棍',1),
(2123219848727202366,'电击器',1),
(2123219848727202367,'电棍',1),
(2123219848727202368,'警棍',1),
(2123219848727202369,'催泪瓦斯',1),
(2123219848727202370,'防狼喷雾',1),
(2123219848727202371,'十字弓',1),
(2123219848727202372,'蒙汗药',1),
(2123219848727202373,'听话水',1),
(2123219848727202374,'乖乖水',1),
(2123219848727202375,'迷奸药',1),
(2123219848727202376,'麻醉剂',1),
(2123219848727202377,'免费送',1),
(2123219848727202378,'免费试用',1),
(2123219848727202379,'领券',1),
(2123219848727202380,'返现',1),
(2123219848727202381,'返利',1),
(2123219848727202382,'佣金',1),
(2123219848727202383,'提成',1),
(2123219848727202384,'分销',1),
(2123219848727202385,'诚招代理',1),
(2123219848727202386,'一件代发',1),
(2123219848727202387,'刷流量',1),
(2123219848727202388,'刷评论',1),
(2123219848727202389,'刷好评',1),
(2123219848727202390,'好评返现',1),
(2123219848727202391,'刷信誉',1),
(2123219848727202392,'刷钻',1),
(2123219848727202393,'信誉卡',1),
(2123219848727202394,'兼职群',1),
(2123219848727202395,'赚钱',1),
(2123219848727202396,'招兼职',1),
(2123219848727202397,'日结兼职',1),
(2123219848727202398,'手工活',1),
(2123219848727202399,'在家兼职',1),
(2123219848727202400,'手机兼职',1),
(2123219848727202401,'风口项目',1),
(2123219848727202402,'零投资',1),
(2123219848727202403,'零成本',1),
(2123219848727202404,'手把手',1),
(2123219848727202405,'包会',1),
(2123219848727202406,'黑客',1),
(2123219848727202407,'黑客工具',1),
(2123219848727202408,'黑客技术',1),
(2123219848727202409,'网络攻击',1),
(2123219848727202410,'DDoS',1),
(2123219848727202411,'肉鸡',1),
(2123219848727202412,'木马',1),
(2123219848727202413,'病毒',1),
(2123219848727202414,'蠕虫',1),
(2123219848727202415,'社工',1),
(2123219848727202416,'社会工程学',1),
(2123219848727202417,'撞库软件',1),
(2123219848727202418,'爆破',1),
(2123219848727202419,'破解软件',1),
(2123219848727202420,'注册机',1),
(2123219848727202421,'激活码',1),
(2123219848727202422,'破解补丁',1),
(2123219848727202423,'越狱',1),
(2123219848727202424,'提权',1),
(2123219848727202425,'漏洞利用',1),
(2123219848727202426,'0day',1),
(2123219848727202427,'抓包',1),
(2123219848727202428,'嗅探',1),
(2123219848727202429,'窃听',1),
(2123219848727202430,'针孔摄像头',1),
(2123219848727202431,'偷拍器',1),
(2123219848727202432,'查开房',1),
(2123219848727202433,'开房记录',1),
(2123219848727202434,'身份证查询',1),
(2123219848727202435,'个人信息查询',1),
(2123219848727202436,'户籍查询',1),
(2123219848727202437,'征信查询',1),
(2123219848727202438,'反催收',1),
(2123219848727202439,'撸口子',1),
(2123219848727202440,'短信轰炸',1),
(2123219848727202441,'炸弹短信',1),
(2123219848727202442,'恐怖主义',1),
(2123219848727202443,'恐怖袭击',1),
(2123219848727202444,'暴力血腥',1),
(2123219848727202445,'血腥',1),
(2123219848727202446,'凶杀',1),
(2123219848727202447,'杀人',1),
(2123219848727202448,'谋杀',1),
(2123219848727202449,'自残',1),
(2123219848727202450,'自杀',1),
(2123219848727202451,'自杀方法',1),
(2123219848727202452,'约架',1),
(2123219848727202453,'打架',1),
(2123219848727202454,'斗殴',1),
(2123219848727202455,'群殴',1),
(2123219848727202456,'黑社会',1),
(2123219848727202457,'涉黑',1),
(2123219848727202458,'帮派',1),
(2123219848727202459,'走私',1),
(2123219848727202460,'偷渡',1),
(2123219848727202461,'行贿',1),
(2123219848727202462,'受贿',1),
(2123219848727202463,'贪污',1),
(2123219848727202464,'职务侵占',1),
(2123219848727202465,'挪用公款',1),
(2123219848727202466,'逃税',1),
(2123219848727202467,'偷税',1),
(2123219848727202468,'盗窃',1),
(2123219848727202469,'抢劫',1),
(2123219848727202470,'拐卖',1),
(2123219848727202471,'买卖人口',1),
(2123219848727202472,'非法拘禁',1),
(2123219848727202473,'非法交易',1),
(2123219848727202474,'违禁药品',1),
(2123219848727202475,'卖卵',1),
(2123219848727202476,'捐卵',1),
(2123219848727202477,'卵子交易',1),
(2123219848727202478,'代孕中介',1),
(2123219848727202479,'黑中介',1),
(2123219848727202480,'色情陪侍',1),
(2123219848727202481,'提供陪侍',1),
(2123219848727202482,'交友群',1),
(2123219848727202483,'单身交友',1),
(2123219848727202484,'成年交友',1),
(2123219848727202485,'私密交友',1),
(2123219848727202486,'深夜福利',1);


-- ===== 内置标签扩充：围绕 8 个一级板块补充话题标签（INSERT IGNORE + (board_key,name) 唯一键保证幂等）=====
-- general（公司要闻）
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798410001,'general','行业动态',10,1),
(2123219848798410002,'general','业绩发布',11,1),
(2123219848798410003,'general','战略合作',12,1),
(2123219848798410004,'general','品牌宣传',13,1),
(2123219848798410005,'general','社会责任',14,1),
(2123219848798410006,'general','年度总结',15,1),
(2123219848798410007,'general','一线传真',16,1),
(2123219848798410008,'general','政策宣贯',17,1);
-- frontend（文化故事）
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798410009,'frontend','人物专访',10,1),
(2123219848798410010,'frontend','职场感悟',11,1),
(2123219848798410011,'frontend','读书分享',12,1),
(2123219848798410012,'frontend','团建活动',13,1),
(2123219848798410013,'frontend','节日特辑',14,1),
(2123219848798410014,'frontend','文化随笔',15,1),
(2123219848798410015,'frontend','暖心瞬间',16,1),
(2123219848798410016,'frontend','故事征集',17,1);
-- devops（业务圈儿）
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798410025,'devops','销售经验',10,1),
(2123219848798410026,'devops','项目复盘',11,1),
(2123219848798410027,'devops','行业洞察',12,1),
(2123219848798410028,'devops','客户需求',13,1),
(2123219848798410029,'devops','合同签署',14,1),
(2123219848798410030,'devops','市场活动',15,1),
(2123219848798410031,'devops','业务技能',16,1),
(2123219848798410032,'devops','团队协作',17,1);
-- design（数字加油站）
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798410033,'design','工具推荐',10,1),
(2123219848798410034,'design','办公技巧',11,1),
(2123219848798410035,'design','AI工具',12,1),
(2123219848798410036,'design','低代码',13,1),
(2123219848798410037,'design','数据可视化',14,1),
(2123219848798410038,'design','数字化转型',15,1),
(2123219848798410039,'design','效率提升',16,1),
(2123219848798410040,'design','技术分享',17,1);
-- career（培训培养）
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798410041,'career','入职指南',10,1),
(2123219848798410042,'career','职业规划',11,1),
(2123219848798410043,'career','培训课程',12,1),
(2123219848798410044,'career','导师带教',13,1),
(2123219848798410045,'career','考试认证',14,1),
(2123219848798410046,'career','技能大赛',15,1),
(2123219848798410047,'career','学习心得',16,1),
(2123219848798410048,'career','成长故事',17,1);
-- voice（不吐不快）
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798410049,'voice','意见征集',10,1),
(2123219848798410050,'voice','合理化建议',11,1),
(2123219848798410051,'voice','员工心声',12,1),
(2123219848798410052,'voice','微心愿',13,1),
(2123219848798410053,'voice','互帮互助',14,1),
(2123219848798410054,'voice','曝光台',15,1),
(2123219848798410055,'voice','吐槽大会',16,1),
(2123219848798410056,'voice','心声留言',17,1);
-- life（生活驿站）
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798410057,'life','生活妙招',10,1),
(2123219848798410058,'life','家政服务',11,1),
(2123219848798410059,'life','健身运动',12,1),
(2123219848798410060,'life','养生保健',13,1),
(2123219848798410061,'life','育儿经验',14,1),
(2123219848798410062,'life','出行攻略',15,1),
(2123219848798410063,'life','休闲娱乐',16,1),
(2123219848798410064,'life','消费指南',17,1);

-- ===== 内置标签二次扩充：7个板块各补7个，每板块达20个（幂等）=====
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798411001,'general','数智转型',18,1),
(2123219848798411002,'general','客户成功',19,1),
(2123219848798411003,'general','开门红',20,1),
(2123219848798411004,'general','安全生产',21,1),
(2123219848798411005,'general','质量月',22,1),
(2123219848798411006,'general','创新大赛',23,1),
(2123219848798411007,'general','文化周',24,1);
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798411008,'frontend','文化沙龙',18,1),
(2123219848798411009,'frontend','运动瞬间',19,1),
(2123219848798411010,'frontend','公益志愿',20,1),
(2123219848798411011,'frontend','节日礼物',21,1),
(2123219848798411012,'frontend','办公美学',22,1),
(2123219848798411013,'frontend','旅行日记',23,1),
(2123219848798411014,'frontend','心得随笔',24,1);
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798411015,'devops','交付复盘',18,1),
(2123219848798411016,'devops','客户拜访',19,1),
(2123219848798411017,'devops','商机跟进',20,1),
(2123219848798411018,'devops','技术方案',21,1),
(2123219848798411019,'devops','跨界协同',22,1),
(2123219848798411020,'devops','成本管控',23,1),
(2123219848798411021,'devops','服务口碑',24,1);
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798411022,'design','运维实践',18,1),
(2123219848798411023,'design','系统监控',19,1),
(2123219848798411024,'design','数据治理',20,1),
(2123219848798411025,'design','架构设计',21,1),
(2123219848798411026,'design','信息安全',22,1),
(2123219848798411027,'design','AI提效',23,1),
(2123219848798411028,'design','工具测评',24,1);
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798411029,'career','管培生',18,1),
(2123219848798411030,'career','内训现场',19,1),
(2123219848798411031,'career','外部培训',20,1),
(2123219848798411032,'career','读书会',21,1),
(2123219848798411033,'career','导师制',22,1),
(2123219848798411034,'career','资格认证',23,1),
(2123219848798411035,'career','新人指南',24,1);
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798411036,'voice','金点子',18,1),
(2123219848798411037,'voice','吐槽墙',19,1),
(2123219848798411038,'voice','民意收集',20,1),
(2123219848798411039,'voice','暖心建议',21,1),
(2123219848798411040,'voice','流程吐槽',22,1),
(2123219848798411041,'voice','直通车',23,1),
(2123219848798411042,'voice','回音壁',24,1);
INSERT IGNORE INTO forum_tag(id,board_key,name,sort_no,enabled) VALUES
(2123219848798411043,'life','通勤指南',18,1),
(2123219848798411044,'life','下午茶',19,1),
(2123219848798411045,'life','健身打卡',20,1),
(2123219848798411046,'life','育儿分享',21,1),
(2123219848798411047,'life','居家收纳',22,1),
(2123219848798411048,'life','观影推荐',23,1),
(2123219848798411049,'life','周末去哪儿',24,1);
