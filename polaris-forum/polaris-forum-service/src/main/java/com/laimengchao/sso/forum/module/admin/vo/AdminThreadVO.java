package com.laimengchao.sso.forum.module.admin.vo;

import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminThreadVO extends ThreadVO {

    private Boolean deleted;
}
