package com.laimengchao.sso.forum.module.report.mapper;

import com.laimengchao.sso.forum.module.report.dto.ReportDetailDTO;
import com.laimengchao.sso.forum.module.report.entity.Report;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReportMapper {

    int insert(Report values);

    ReportDetailDTO findById(@Param("id") long id);

    List<ReportDetailDTO> list(@Param("status") String status, @Param("targetType") String targetType);

    Report forUpdate(@Param("id") long id);

    int handle(@Param("id") long id, @Param("status") String status, @Param("handlerAccountId") long handlerAccountId,
               @Param("handlerName") String handlerName, @Param("handleResult") String handleResult);
}
