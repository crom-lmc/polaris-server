package com.laimengchao.sso.cms.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Cms 业务错误码段（通用段见 common 的 ErrorCode；本枚举只含 CMS 业务码）。
 * 实现 IErrorCode 即可接入统一的 R / BizException / GlobalExceptionHandler。
 */
@Getter
@AllArgsConstructor
public enum CmsErrorCode implements IErrorCode {

    DATA_NOT_FOUND(6001, "数据不存在"),
    DATA_ALREADY_EXISTS(6002, "数据已存在"),
    CATEGORY_IN_USE(6003, "该分类下仍有文章，不能删除"),
    TAG_IN_USE(6004, "该标签已被文章使用，不能删除"),
    ILLEGAL_STATE(6005, "当前状态不允许该操作"),
    FILE_TYPE_NOT_ALLOWED(6006, "文件类型不允许"),
    FILE_TOO_LARGE(6007, "文件超过大小限制"),
    UPLOAD_FAILED(6008, "文件上传失败"),
    SEARCH_FAILED(6009, "检索失败"),
    /** 向 settings 审批中心发起内容发布审批失败 */
    APPROVAL_START_FAILED(6010, "发起审批失败"),
    /** 撤回在途审批单失败 */
    APPROVAL_WITHDRAW_FAILED(6011, "撤回审批失败");

    private final int code;
    private final String message;
}
