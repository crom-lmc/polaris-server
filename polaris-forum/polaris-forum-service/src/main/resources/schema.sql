CREATE TABLE IF NOT EXISTS forum_thread (
  id BIGINT NOT NULL COMMENT '主键ID',
  kind VARCHAR(16) NOT NULL DEFAULT 'topic' COMMENT '内容类型：feed动态、topic话题、question问答、article文章',
  board_key VARCHAR(32) NOT NULL COMMENT '所属板块标识',
  title VARCHAR(160) NOT NULL DEFAULT '' COMMENT '内容标题，动态为空',
  content TEXT NOT NULL COMMENT '正文内容，受限富文本HTML或历史纯文本',
  excerpt VARCHAR(600) NOT NULL DEFAULT '' COMMENT '列表纯文本摘要',
  tags VARCHAR(600) NOT NULL DEFAULT '[]' COMMENT '标签JSON数组',
  author_account_id BIGINT NOT NULL DEFAULT 0 COMMENT '作者账号ID',
  author_name VARCHAR(80) NOT NULL DEFAULT '' COMMENT '作者显示名称快照',
  reward INT NOT NULL DEFAULT 0 COMMENT '问答悬赏积分',
  cover_attachment_id BIGINT NULL COMMENT '文章封面附件ID',
  replies_count INT NOT NULL DEFAULT 0 COMMENT '评论回复数',
  views_count INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
  likes_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  pinned TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0否、1是',
  solved TINYINT NOT NULL DEFAULT 0 COMMENT '问答是否已解决：0否、1是',
  recommended TINYINT NOT NULL DEFAULT 0 COMMENT '是否推荐：0否、1是',
  recommended_at DATETIME NULL COMMENT '推荐时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否软删除：0否、1是',
  PRIMARY KEY (id),
  KEY idx_thread_list (deleted, kind, board_key, pinned, created_at),
  KEY idx_thread_author (author_account_id, created_at),
  KEY idx_thread_recommended (deleted, recommended, recommended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛四类内容';

CREATE TABLE IF NOT EXISTS forum_comment (
  id BIGINT NOT NULL COMMENT '主键ID',
  thread_id BIGINT NOT NULL COMMENT '所属帖子ID',
  parent_comment_id BIGINT NULL COMMENT '父评论ID，一级评论为空',
  author_account_id BIGINT NOT NULL COMMENT '作者账号ID',
  author_name VARCHAR(80) NOT NULL DEFAULT '' COMMENT '作者显示名称快照',
  content VARCHAR(2000) NOT NULL COMMENT '评论纯文本内容',
  likes_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否软删除：0否、1是',
  PRIMARY KEY (id),
  KEY idx_comment_thread (thread_id, deleted, created_at),
  KEY idx_comment_parent (parent_comment_id, deleted, created_at),
  KEY idx_comment_author (author_account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛评论';

CREATE TABLE IF NOT EXISTS forum_like (
  id BIGINT NOT NULL COMMENT '主键ID',
  account_id BIGINT NOT NULL COMMENT '点赞账号ID',
  target_type VARCHAR(16) NOT NULL COMMENT '点赞目标类型：thread帖子、comment评论',
  target_id BIGINT NOT NULL COMMENT '点赞目标ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_like (account_id, target_type, target_id),
  KEY idx_like_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞唯一记录';

CREATE TABLE IF NOT EXISTS forum_member (
  account_id BIGINT NOT NULL COMMENT '论坛成员账号ID',
  username VARCHAR(80) NOT NULL DEFAULT '' COMMENT '登录用户名',
  nickname VARCHAR(80) NOT NULL DEFAULT '' COMMENT '成员昵称',
  bio VARCHAR(500) NOT NULL DEFAULT '' COMMENT '个人简介',
  location VARCHAR(100) NOT NULL DEFAULT '' COMMENT '所在地',
  website VARCHAR(255) NOT NULL DEFAULT '' COMMENT '个人网站',
  avatar_attachment_id BIGINT NULL COMMENT '头像附件ID',
  cover_attachment_id BIGINT NULL COMMENT '个人主页封面附件ID',
  points INT NOT NULL DEFAULT 100 COMMENT '当前积分余额（可消费）',
  experience INT NOT NULL DEFAULT 0 COMMENT '累计经验值（只增不减，用于等级晋升）',
  threads_count INT NOT NULL DEFAULT 0 COMMENT '发帖数',
  likes_received INT NOT NULL DEFAULT 0 COMMENT '帖子获赞总数',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛成员与积分账户';

CREATE TABLE IF NOT EXISTS forum_point_log (
  id BIGINT NOT NULL COMMENT '主键ID',
  account_id BIGINT NOT NULL COMMENT '积分账号ID',
  change_points INT NOT NULL COMMENT '积分变动值，正数增加、负数减少',
  balance INT NOT NULL COMMENT '变动后积分余额',
  biz_type VARCHAR(32) NOT NULL COMMENT '积分业务类型',
  biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联业务标识',
  remark VARCHAR(160) NOT NULL DEFAULT '' COMMENT '积分变动备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_point_account (account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

CREATE TABLE IF NOT EXISTS forum_checkin (
  id BIGINT NOT NULL COMMENT '主键ID',
  account_id BIGINT NOT NULL COMMENT '签到账号ID',
  checkin_date DATE NOT NULL COMMENT '签到日期',
  points INT NOT NULL DEFAULT 10 COMMENT '本次签到获得积分',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkin (account_id, checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日签到';

CREATE TABLE IF NOT EXISTS forum_lottery_log (
  id BIGINT NOT NULL COMMENT '主键ID',
  account_id BIGINT NOT NULL COMMENT '抽奖账号ID',
  prize_key VARCHAR(32) NOT NULL COMMENT '奖品标识',
  prize_name VARCHAR(80) NOT NULL COMMENT '奖品名称快照',
  cost_points INT NOT NULL DEFAULT 50 COMMENT '本次抽奖消耗积分',
  award_points INT NOT NULL DEFAULT 0 COMMENT '本次抽奖获得积分',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_lottery_account (account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分抽奖记录';

CREATE TABLE IF NOT EXISTS forum_lottery_config (
  id TINYINT NOT NULL COMMENT '配置ID，固定为1',
  mode VARCHAR(16) NOT NULL DEFAULT 'SQUARE' COMMENT '抽奖形态：SQUARE方形九宫格、WHEEL转盘',
  lottery_cost INT NOT NULL DEFAULT 50 COMMENT '每次抽奖消耗积分',
  daily_limit INT NOT NULL DEFAULT 3 COMMENT '每日抽奖次数上限',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛抽奖配置';

CREATE TABLE IF NOT EXISTS forum_lottery_prize (
  id BIGINT NOT NULL COMMENT '主键ID',
  prize_key VARCHAR(32) NOT NULL COMMENT '奖项标识',
  prize_type VARCHAR(16) NOT NULL DEFAULT 'POINTS' COMMENT '奖项类型：POINTS积分、GIFT礼品',
  gift_id BIGINT NULL COMMENT '礼品ID，仅GIFT类型有效，关联forum_gift.id',
  name VARCHAR(80) NOT NULL COMMENT '奖项名称',
  points INT NOT NULL DEFAULT 0 COMMENT '中奖积分，0表示谢谢参与',
  color VARCHAR(16) NOT NULL DEFAULT '#eef0ff' COMMENT '展示背景色',
  weight INT NOT NULL DEFAULT 1 COMMENT '中奖权重，越大概率越高',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，决定展示位置与转动顺序',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_prize_sort (enabled, sort_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛抽奖奖项';

CREATE TABLE IF NOT EXISTS forum_gift (
  id BIGINT NOT NULL COMMENT '主键ID',
  name VARCHAR(100) NOT NULL COMMENT '礼品名称',
  category VARCHAR(32) NOT NULL COMMENT '礼品分类',
  description VARCHAR(300) NOT NULL DEFAULT '' COMMENT '礼品描述',
  points INT NOT NULL COMMENT '兑换所需积分',
  stock INT NOT NULL DEFAULT 0 COMMENT '当前库存',
  icon VARCHAR(32) NOT NULL DEFAULT 'gift' COMMENT '前端图标标识',
  image_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '礼品展示图片地址，支持站内路径或 http(s) 外链',
  min_level INT NOT NULL DEFAULT 1 COMMENT '兑换所需最低社区等级，1-20',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态：0下架、1上架',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分礼品';

CREATE TABLE IF NOT EXISTS forum_exchange (
  id BIGINT NOT NULL COMMENT '主键ID',
  account_id BIGINT NOT NULL COMMENT '兑换账号ID',
  gift_id BIGINT NOT NULL COMMENT '礼品ID',
  gift_name VARCHAR(100) NOT NULL COMMENT '礼品名称快照',
  points INT NOT NULL COMMENT '兑换消耗积分，抽奖中奖礼品为0',
  source VARCHAR(16) NOT NULL DEFAULT 'EXCHANGE' COMMENT '来源：EXCHANGE兑换、LOTTERY抽奖',
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_ISSUE' COMMENT '状态：PENDING_ISSUE待发放、SHIPPING物流运输中、ISSUED已发放',
  tracking_no VARCHAR(100) NOT NULL DEFAULT '' COMMENT '快递单号',
  issued_by BIGINT NULL COMMENT '签收确认管理员账号ID',
  issued_by_name VARCHAR(80) NOT NULL DEFAULT '' COMMENT '签收确认管理员名称快照',
  issued_at DATETIME NULL COMMENT '签收确认时间',
  issue_remark VARCHAR(500) NOT NULL DEFAULT '' COMMENT '发放或签收备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_exchange_account (account_id, created_at),
  KEY idx_exchange_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼品兑换记录';

CREATE TABLE IF NOT EXISTS forum_draft (
  id BIGINT NOT NULL COMMENT '主键ID',
  account_id BIGINT NOT NULL COMMENT '草稿所有者账号ID',
  kind VARCHAR(16) NOT NULL DEFAULT 'topic' COMMENT '内容类型：feed动态、topic话题、question问答、article文章',
  board_key VARCHAR(32) NOT NULL COMMENT '所属板块标识',
  title VARCHAR(160) NOT NULL DEFAULT '' COMMENT '草稿标题，动态为空',
  content TEXT NOT NULL COMMENT '草稿正文，受限富文本HTML或历史纯文本',
  tags VARCHAR(600) NOT NULL DEFAULT '[]' COMMENT '标签JSON数组',
  reward INT NOT NULL DEFAULT 0 COMMENT '问答悬赏积分',
  cover_attachment_id BIGINT NULL COMMENT '文章封面附件ID',
  poll_config TEXT NULL COMMENT '投票配置JSON',
  published_thread_id BIGINT NULL COMMENT '已发布帖子ID，未发布为空',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否软删除：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_draft_owner (account_id, deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛服务端草稿';

CREATE TABLE IF NOT EXISTS forum_attachment (
  id BIGINT NOT NULL COMMENT '主键ID',
  owner_account_id BIGINT NOT NULL COMMENT '附件所有者账号ID',
  usage_type VARCHAR(16) NOT NULL DEFAULT 'ATTACHMENT' COMMENT '附件用途类型',
  original_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '原始文件名',
  storage_path VARCHAR(500) NOT NULL COMMENT '服务器存储相对路径',
  mime_type VARCHAR(100) NOT NULL COMMENT '文件MIME类型',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小，单位字节',
  thread_id BIGINT NULL COMMENT '绑定帖子ID',
  draft_id BIGINT NULL COMMENT '绑定草稿ID',
  profile_account_id BIGINT NULL COMMENT '绑定个人资料账号ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_attachment_owner (owner_account_id, created_at),
  KEY idx_attachment_draft (draft_id),
  KEY idx_attachment_thread (thread_id),
  KEY idx_attachment_profile (profile_account_id, usage_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛附件';

CREATE TABLE IF NOT EXISTS forum_poll (
  id BIGINT NOT NULL COMMENT '主键ID',
  thread_id BIGINT NOT NULL COMMENT '所属帖子ID',
  mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '投票模式：SINGLE单选、MULTIPLE多选',
  max_choices INT NOT NULL DEFAULT 1 COMMENT '最多可选择的选项数',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_poll_thread (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛投票';

CREATE TABLE IF NOT EXISTS forum_poll_option (
  id BIGINT NOT NULL COMMENT '主键ID',
  poll_id BIGINT NOT NULL COMMENT '所属投票ID',
  option_text VARCHAR(200) NOT NULL COMMENT '选项文本',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  PRIMARY KEY (id),
  KEY idx_poll_option_sort (poll_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛投票选项';

CREATE TABLE IF NOT EXISTS forum_poll_vote (
  id BIGINT NOT NULL COMMENT '主键ID',
  poll_id BIGINT NOT NULL COMMENT '所属投票ID',
  account_id BIGINT NOT NULL COMMENT '投票账号ID',
  option_id BIGINT NOT NULL COMMENT '所选选项ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_poll_vote (poll_id, account_id, option_id),
  KEY idx_poll_vote_account (poll_id, account_id),
  KEY idx_poll_vote_option (option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛投票选票';

CREATE TABLE IF NOT EXISTS forum_sensitive_word (
  id BIGINT NOT NULL COMMENT '主键ID',
  word VARCHAR(100) NOT NULL COMMENT '敏感词',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sensitive_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛敏感词';

CREATE TABLE IF NOT EXISTS forum_favorite (
  id BIGINT NOT NULL COMMENT '主键ID',
  account_id BIGINT NOT NULL COMMENT '收藏账号ID',
  thread_id BIGINT NOT NULL COMMENT '收藏帖子ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_account_thread (account_id, thread_id),
  KEY idx_favorite_thread (thread_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子收藏';

CREATE TABLE IF NOT EXISTS forum_badge (
  id BIGINT NOT NULL COMMENT '主键ID',
  name VARCHAR(80) NOT NULL COMMENT '勋章名称',
  description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '勋章描述',
  icon VARCHAR(64) NOT NULL DEFAULT '' COMMENT '勋章图标标识',
  color VARCHAR(16) NOT NULL DEFAULT '' COMMENT '勋章展示颜色',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  rule_type VARCHAR(32) NOT NULL COMMENT '勋章获得规则类型',
  threshold INT NOT NULL COMMENT '勋章获得规则阈值',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '勋章状态：DRAFT草稿、PUBLISHED已发布、OFFLINE已下线',
  created_by BIGINT NOT NULL COMMENT '创建人账号ID',
  published_at DATETIME NULL COMMENT '发布时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_badge_status (status, sort_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛勋章规则';

CREATE TABLE IF NOT EXISTS forum_level (
  id BIGINT NOT NULL COMMENT '主键ID',
  level_no INT NOT NULL COMMENT '等级序号，从1开始',
  name VARCHAR(80) NOT NULL COMMENT '等级名称',
  min_experience INT NOT NULL COMMENT '达到该等级所需累计经验',
  color VARCHAR(16) NOT NULL DEFAULT '#5b67f1' COMMENT '等级展示颜色',
  icon VARCHAR(64) NOT NULL DEFAULT '' COMMENT '等级图标文本',
  description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '等级说明',
  privileges VARCHAR(500) NOT NULL DEFAULT '' COMMENT '等级权益',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用、0停用',
  builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置等级：1系统内置(不可删除)、0用户新建',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_level_no (level_no),
  KEY idx_level_enabled (enabled, sort_no, level_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区等级晋升路线';

CREATE TABLE IF NOT EXISTS forum_experience_rule (
  id BIGINT NOT NULL COMMENT '主键ID',
  rule_key VARCHAR(32) NOT NULL COMMENT '行为标识：POST发帖/COMMENT回复等',
  name VARCHAR(80) NOT NULL COMMENT '行为名称',
  experience INT NOT NULL DEFAULT 0 COMMENT '每次行为增加的经验值',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用、0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_exp_rule_key (rule_key),
  KEY idx_exp_rule_enabled (enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区经验获取规则';

CREATE TABLE IF NOT EXISTS forum_member_badge (
  id BIGINT NOT NULL COMMENT '主键ID',
  account_id BIGINT NOT NULL COMMENT '获得勋章的成员账号ID',
  badge_id BIGINT NOT NULL COMMENT '勋章ID',
  awarded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_badge (account_id, badge_id),
  KEY idx_member_badge_badge (badge_id, awarded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛成员已获得勋章';

CREATE TABLE IF NOT EXISTS forum_notification (
  id BIGINT NOT NULL COMMENT '主键ID',
  recipient_account_id BIGINT NOT NULL COMMENT '接收人账号ID',
  actor_account_id BIGINT NULL COMMENT '触发人账号ID，系统通知为空',
  type VARCHAR(32) NOT NULL COMMENT '通知类型',
  biz_id BIGINT NOT NULL COMMENT '关联业务ID',
  thread_id BIGINT NULL COMMENT '关联帖子ID',
  title VARCHAR(160) NOT NULL COMMENT '通知标题',
  summary VARCHAR(500) NOT NULL DEFAULT '' COMMENT '通知摘要',
  read_at DATETIME NULL COMMENT '阅读时间，未读为空',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_recipient_biz (recipient_account_id, type, biz_id),
  KEY idx_notification_recipient_read (recipient_account_id, read_at, created_at),
  KEY idx_notification_thread (thread_id, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛独立通知';

CREATE TABLE IF NOT EXISTS forum_board (
  board_key VARCHAR(32) NOT NULL COMMENT '板块唯一标识',
  parent_key VARCHAR(32) NOT NULL DEFAULT '' COMMENT '父级板块标识，为空表示一级板块',
  name VARCHAR(80) NOT NULL COMMENT '板块名称',
  description VARCHAR(300) NOT NULL DEFAULT '' COMMENT '板块描述',
  color VARCHAR(16) NOT NULL DEFAULT '#5b67f1' COMMENT '板块展示颜色',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (board_key),
  KEY idx_board_parent (parent_key, enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛板块（一二级）';

CREATE TABLE IF NOT EXISTS forum_tag (
  id BIGINT NOT NULL COMMENT '主键ID',
  board_key VARCHAR(32) NOT NULL COMMENT '所属板块标识',
  name VARCHAR(20) NOT NULL COMMENT '标签名称',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_board_name (board_key, name),
  KEY idx_tag_board (board_key, enabled, sort_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛板块标签';

CREATE TABLE IF NOT EXISTS forum_follow (
  account_id BIGINT NOT NULL COMMENT '关注人账号ID',
  followed_account_id BIGINT NOT NULL COMMENT '被关注人账号ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (account_id, followed_account_id),
  KEY idx_follow_followed (followed_account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛成员关注关系';

CREATE TABLE IF NOT EXISTS forum_report (
  id BIGINT NOT NULL COMMENT '主键ID',
  reporter_account_id BIGINT NOT NULL COMMENT '举报人账号ID',
  target_type VARCHAR(16) NOT NULL COMMENT '举报目标类型：thread帖子、comment评论',
  target_id BIGINT NOT NULL COMMENT '举报目标ID',
  thread_id BIGINT NULL COMMENT '关联帖子ID',
  reason VARCHAR(32) NOT NULL COMMENT '举报原因代码',
  detail VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '举报补充说明',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING待处理、RESOLVED已处理、REJECTED已驳回',
  handler_account_id BIGINT NULL COMMENT '处理人账号ID',
  handler_name VARCHAR(80) NOT NULL DEFAULT '' COMMENT '处理人名称快照',
  handle_result VARCHAR(500) NOT NULL DEFAULT '' COMMENT '处理结果说明',
  handled_at DATETIME NULL COMMENT '处理时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_reporter_target (reporter_account_id, target_type, target_id),
  KEY idx_report_status (status, created_at),
  KEY idx_report_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛内容举报';

CREATE TABLE IF NOT EXISTS forum_site_config (
  id TINYINT NOT NULL COMMENT '配置ID，固定为1',
  about_content TEXT NOT NULL COMMENT '关于站点内容',
  footer_text VARCHAR(500) NOT NULL DEFAULT '' COMMENT '页脚文案',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛站点配置';

CREATE TABLE IF NOT EXISTS forum_friend_link (
  id BIGINT NOT NULL COMMENT '主键ID',
  name VARCHAR(80) NOT NULL COMMENT '友情链接名称',
  url VARCHAR(500) NOT NULL COMMENT '友情链接地址，仅允许HTTP或HTTPS',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_friend_link (enabled, sort_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛友情链接';

CREATE TABLE IF NOT EXISTS forum_banner (
  id BIGINT NOT NULL COMMENT '主键ID',
  title VARCHAR(120) NOT NULL DEFAULT '' COMMENT '轮播图标题',
  image_attachment_id BIGINT NULL COMMENT '轮播图片附件ID',
  link_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '点击跳转地址，http/https 或站内路径',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_banner (enabled, sort_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛首页轮播图';

CREATE TABLE IF NOT EXISTS forum_id_migration (
  table_name VARCHAR(64) NOT NULL COMMENT '论坛表名',
  old_id BIGINT NOT NULL COMMENT '迁移前自增主键ID',
  new_id BIGINT NOT NULL COMMENT '迁移后雪花主键ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '映射创建时间',
  PRIMARY KEY (table_name, old_id),
  UNIQUE KEY uk_id_migration_new (table_name, new_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛历史自增ID与雪花ID永久映射';

CREATE TABLE IF NOT EXISTS forum_schema_migration (
  version VARCHAR(64) NOT NULL COMMENT '数据库迁移版本',
  completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '迁移完成时间',
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛数据库迁移完成记录';
