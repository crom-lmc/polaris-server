package com.laimengchao.sso.forum.module.admin.mapper;

import com.laimengchao.sso.forum.module.admin.entity.SensitiveWord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SensitiveWordMapper {

    List<String> listEnabled();

    List<SensitiveWord> listAll();

    /** 分页查询：服务端 LIMIT，避免一次返回全部敏感词。 */
    List<SensitiveWord> listAllPaged(@Param("offset") long offset, @Param("size") int size);

    long countAll();

    int insert(SensitiveWord values);

    int setEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    int delete(@Param("id") long id);
}
