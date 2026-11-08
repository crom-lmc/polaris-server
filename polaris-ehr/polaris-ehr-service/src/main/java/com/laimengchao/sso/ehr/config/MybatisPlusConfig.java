package com.laimengchao.sso.ehr.config;

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
                    fillIfNull(metaObject, "createdAt", now);
                }
                if (metaObject.hasSetter("updatedAt")) {
                    fillIfNull(metaObject, "updatedAt", now);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // Tag 等实体没有 updated_at 列，直接填会抛 ReflectionException
                if (metaObject.hasSetter("updatedAt")) {
                    strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                }
            }

            private void fillIfNull(MetaObject metaObject, String field, LocalDateTime value) {
                Object current = metaObject.getValue(field);
                if (current == null) {
                    strictInsertFill(metaObject, field, LocalDateTime.class, value);
                }
            }
        };
    }
}
