package com.laimengchao.sso.forum.config;

import com.laimengchao.sso.forum.module.admin.mapper.AdminMapper;
import com.laimengchao.sso.forum.module.admin.mapper.SensitiveWordMapper;
import com.laimengchao.sso.forum.module.attachment.mapper.AttachmentMapper;
import com.laimengchao.sso.forum.module.badge.mapper.BadgeMapper;
import com.laimengchao.sso.forum.module.draft.mapper.DraftMapper;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.interaction.mapper.FavoriteMapper;
import com.laimengchao.sso.forum.module.interaction.mapper.FollowMapper;
import com.laimengchao.sso.forum.module.member.mapper.MemberMapper;
import com.laimengchao.sso.forum.module.notification.mapper.NotificationMapper;
import com.laimengchao.sso.forum.module.poll.mapper.PollMapper;
import com.laimengchao.sso.forum.module.report.mapper.ReportMapper;
import com.laimengchao.sso.forum.module.site.mapper.SiteMapper;
import com.laimengchao.sso.forum.module.taxonomy.mapper.TaxonomyMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForumMapperXmlTest {

    private static final List<MapperResource> MAPPERS = List.of(
            new MapperResource(ForumMapper.class, "mapper/forum/ForumMapper.xml"),
            new MapperResource(AdminMapper.class, "mapper/admin/AdminMapper.xml"),
            new MapperResource(SensitiveWordMapper.class, "mapper/admin/SensitiveWordMapper.xml"),
            new MapperResource(AttachmentMapper.class, "mapper/attachment/AttachmentMapper.xml"),
            new MapperResource(BadgeMapper.class, "mapper/badge/BadgeMapper.xml"),
            new MapperResource(DraftMapper.class, "mapper/draft/DraftMapper.xml"),
            new MapperResource(FavoriteMapper.class, "mapper/interaction/FavoriteMapper.xml"),
            new MapperResource(FollowMapper.class, "mapper/interaction/FollowMapper.xml"),
            new MapperResource(MemberMapper.class, "mapper/member/MemberMapper.xml"),
            new MapperResource(NotificationMapper.class, "mapper/notification/NotificationMapper.xml"),
            new MapperResource(PollMapper.class, "mapper/poll/PollMapper.xml"),
            new MapperResource(ReportMapper.class, "mapper/report/ReportMapper.xml"),
            new MapperResource(SiteMapper.class, "mapper/site/SiteMapper.xml"),
            new MapperResource(TaxonomyMapper.class, "mapper/taxonomy/TaxonomyMapper.xml"));

    @Test
    void parsesEveryMapperAndMatchesAllInterfaceMethods() throws IOException {
        assertEquals(14, MAPPERS.size());
        assertEquals(ForumMapper.class, MAPPERS.get(0).type(), "跨 namespace include 要求 ForumMapper 先解析");

        Configuration configuration = new Configuration();
        Map<Class<?>, Set<String>> expectedMethods = new LinkedHashMap<>();
        Set<String> expectedStatements = new HashSet<>();
        Set<String> expectedResultMaps = new HashSet<>();
        int methodCount = 0;

        for (MapperResource mapper : MAPPERS) {
            Class<?> type = mapper.type();
            String namespace = type.getName();
            List<Method> methods = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> !method.isSynthetic())
                    .toList();
            Set<String> methodNames = methods.stream().map(Method::getName).collect(Collectors.toSet());
            assertEquals(methods.size(), methodNames.size(), namespace + " 不允许重名/重载方法");
            methodCount += methods.size();
            expectedMethods.put(type, methodNames);

            try (InputStream input = resource(mapper.path())) {
                XPathParser xml = new XPathParser(input, true, configuration.getVariables(),
                        new XMLMapperEntityResolver());
                assertEquals(namespace, xml.evalString("/mapper/@namespace"), mapper.path());

                List<XNode> resultMaps = xml.evalNodes("/mapper/resultMap");
                for (XNode resultMap : resultMaps) {
                    expectedResultMaps.add(namespace + "." + resultMap.getStringAttribute("id"));
                }

                List<XNode> statements = new ArrayList<>();
                for (String statementType : List.of("select", "insert", "update", "delete")) {
                    statements.addAll(xml.evalNodes("/mapper/" + statementType));
                }
                assertEquals(methodNames.size(), statements.size(), mapper.path());
                Set<String> statementNames = statements.stream()
                        .map(statement -> statement.getStringAttribute("id"))
                        .collect(Collectors.toSet());
                assertEquals(methodNames, statementNames, mapper.path() + " 的 statement 与接口方法必须一一对应");
            }

            try (InputStream input = resource(mapper.path())) {
                new XMLMapperBuilder(input, configuration, mapper.path(), configuration.getSqlFragments()).parse();
            }
            expectedStatements.addAll(methodNames.stream().map(name -> namespace + "." + name).toList());
        }

        assertEquals(144, methodCount);
        assertEquals(144, expectedStatements.size());
        for (String statementId : expectedStatements) {
            assertTrue(configuration.hasStatement(statementId), statementId);
            MappedStatement statement = configuration.getMappedStatement(statementId);
            assertNotNull(statement.getSqlSource(), statementId);
        }
        for (String resultMapId : expectedResultMaps) {
            assertTrue(configuration.hasResultMap(resultMapId), resultMapId);
            assertNotNull(configuration.getResultMap(resultMapId), resultMapId);
        }

        Set<String> namespaces = MAPPERS.stream().map(mapper -> mapper.type().getName() + ".").collect(Collectors.toSet());
        Set<String> actualStatements = configuration.getMappedStatementNames().stream()
                .filter(id -> namespaces.stream().anyMatch(id::startsWith))
                .collect(Collectors.toSet());
        assertEquals(expectedStatements, actualStatements, "存在没有接口方法对应的 statement");
        assertEquals(144, actualStatements.size());
        assertTrue(configuration.getSqlFragments().containsKey(
                ForumMapper.class.getName() + ".memberColumns"), "跨 XML include 的公共 SQL 片段未注册");
        assertEquals(expectedMethods.get(MemberMapper.class),
                actualStatements.stream()
                        .filter(id -> id.startsWith(MemberMapper.class.getName() + "."))
                        .map(id -> id.substring(id.lastIndexOf('.') + 1))
                        .collect(Collectors.toSet()));
    }

    private static InputStream resource(String path) {
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        assertNotNull(input, "缺少 classpath mapper: " + path);
        return input;
    }

    private record MapperResource(Class<?> type, String path) {
    }
}
