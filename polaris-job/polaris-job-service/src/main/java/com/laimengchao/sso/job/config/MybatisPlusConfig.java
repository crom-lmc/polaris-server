package com.laimengchao.sso.job.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/** MyBatis-Plus：MySQL 5.7 分页 + created_at/updated_at 自动填充 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                if (metaObject.hasSetter("createdAt")) {
                    strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                }
                if (metaObject.hasSetter("updatedAt")) {
                    strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                if (metaObject.hasSetter("updatedAt")) {
                    strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                }
            }
        };
    }
}
