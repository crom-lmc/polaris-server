package com.laimengchao.sso.cms.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.laimengchao.sso.cms.module.content.entity.Article;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /** 回收站列表（@TableLogic 会拦住普通查询，回收站走原生 SQL） */
    @Select("<script>SELECT * FROM cms_article WHERE deleted = 1"
            + " <if test='kw != null and kw != \"\"'> AND (title LIKE CONCAT('%', #{kw}, '%'))</if>"
            + " ORDER BY updated_at DESC</script>")
    IPage<Article> pageRecycle(IPage<Article> page, @Param("kw") String kw);

    /** 从回收站恢复 */
    @Update("UPDATE cms_article SET deleted = 0 WHERE id = #{id}")
    int restore(@Param("id") Long id);

    /** 回收站里的文章（@TableLogic 拦不住原生 SQL） */
    @Select("SELECT * FROM cms_article WHERE id = #{id}")
    Article selectDeleted(@Param("id") Long id);

    /** 物理删除（关联表清理由 service 负责） */
    @Delete("DELETE FROM cms_article WHERE id = #{id}")
    int deleteForever(@Param("id") Long id);

    /** 定时发布扫描：到点的「待发布」文章置为「已发布」 */
    @Select("SELECT * FROM cms_article WHERE deleted = 0 AND status = 2 AND publish_at IS NOT NULL "
            + "AND publish_at <= NOW()")
    List<Article> listDueScheduled();

    /** ngram 全文检索（已发布）；失败时 service 层回退 LIKE */
    @Select("SELECT * FROM cms_article WHERE deleted = 0 AND status = 3 "
            + "AND MATCH(title, content) AGAINST(#{kw} IN NATURAL LANGUAGE MODE) "
            + "ORDER BY pinned DESC, published_at DESC LIMIT #{limit}")
    List<Article> searchFulltext(@Param("kw") String kw, @Param("limit") int limit);

    /** 全文检索兜底 */
    @Select("SELECT * FROM cms_article WHERE deleted = 0 AND status = 3 "
            + "AND (title LIKE CONCAT('%', #{kw}, '%') OR content LIKE CONCAT('%', #{kw}, '%')) "
            + "ORDER BY pinned DESC, published_at DESC LIMIT #{limit}")
    List<Article> searchLike(@Param("kw") String kw, @Param("limit") int limit);

    /** 统计某分类的文章数（不含回收站） */
    @Select("SELECT COUNT(*) FROM cms_article WHERE deleted = 0 AND category_id = #{categoryId}")
    long countByCategory(@Param("categoryId") Long categoryId);

    /** 统计某标签的文章数（不含回收站）：标签已内联到 cms_article.tag_id */
    @Select("SELECT COUNT(*) FROM cms_article WHERE deleted = 0 AND tag_id = #{tagId}")
    long countByTag(@Param("tagId") Long tagId);
}
