package com.laimengchao.sso.blog.module.media.controller;

import com.laimengchao.sso.blog.common.PageResult;
import com.laimengchao.sso.blog.common.PageSizes;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.blog.common.log.OpLog;
import com.laimengchao.sso.blog.module.media.dto.MediaFolderVO;
import com.laimengchao.sso.blog.module.media.entity.Media;
import com.laimengchao.sso.blog.module.media.service.MediaFolderService;
import com.laimengchao.sso.blog.module.media.service.MediaService;
import com.laimengchao.sso.blog.module.setting.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import com.laimengchao.sso.blog.security.Permit;
import com.laimengchao.sso.common.security.PermissionCodes;

/**
 * 后台媒体库（author 角色可访问）。
 */
@RestController
@RequestMapping("/api/blog/admin/media")
@RequiredArgsConstructor
@Tag(name = "后台-媒体库", description = "上传（普通 / 分片）、列表、目录、删除")
public class AdminMediaController {

    private final MediaService mediaService;
    private final MediaFolderService mediaFolderService;
    private final SettingService settingService;

    @GetMapping
    @Operation(summary = "媒体列表", description = "folderId：不传=全部，0=未分组，>0=指定目录")
    @Permit(PermissionCodes.MEDIA_LIST)
    public R<PageResult<Media>> page(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(required = false) Long size,
                                     @RequestParam(required = false) String folder,
                                     @RequestParam(required = false) Long folderId,
                                     @RequestParam(required = false) String keyword) {
        long pageSize = PageSizes.resolve(size, settingService.getInt("page_size", 10));
        return R.ok(mediaService.page(page, pageSize, folder, folderId, keyword));
    }

    @PutMapping("/{id}")
    @OpLog(module = "media", action = "update")
    @Operation(summary = "修改媒体文件", description = "重命名与移动目录；两个字段都可选，只更新传入的字段")
    @Permit(PermissionCodes.MEDIA_EDIT)
    public R<Void> update(@PathVariable Long id, @RequestBody UpdateMediaRequest request) {
        if (request == null) {
            throw new com.laimengchao.sso.common.BizException(com.laimengchao.sso.common.ErrorCode.PARAM_ERROR);
        }
        mediaService.update(id, request.getName(), request.getFolderId());
        return R.ok();
    }

    @GetMapping("/folders")
    @Operation(summary = "目录列表（存储路径去重，兼容保留）")
    @Permit(PermissionCodes.MEDIA_LIST)
    public R<List<String>> folders() {
        return R.ok(mediaService.folders());
    }

    @GetMapping("/folders/tree")
    @Operation(summary = "目录树（含各目录媒体数）")
    @Permit(PermissionCodes.MEDIA_LIST)
    public R<List<MediaFolderVO>> folderTree() {
        return R.ok(mediaFolderService.tree());
    }

    @PostMapping("/folders")
    @OpLog(module = "media", action = "folder-create")
    @Operation(summary = "新增目录", description = "parentId 为 0 或不传时创建顶级目录")
    @Permit(PermissionCodes.MEDIA_EDIT)
    public R<Long> createFolder(@RequestBody FolderRequest request) {
        return R.ok(mediaFolderService.create(request.getName(), request.getParentId()));
    }

    @PutMapping("/folders/{id}")
    @OpLog(module = "media", action = "folder-rename")
    @Operation(summary = "重命名目录")
    @Permit(PermissionCodes.MEDIA_EDIT)
    public R<Void> renameFolder(@PathVariable Long id, @RequestBody FolderRequest request) {
        mediaFolderService.rename(id, request.getName());
        return R.ok();
    }

    @DeleteMapping("/folders/{id}")
    @OpLog(module = "media", action = "folder-delete")
    @Operation(summary = "删除目录", description = "仅空目录（无子目录且无文件）可删除")
    @Permit(PermissionCodes.MEDIA_DELETE)
    public R<Void> deleteFolder(@PathVariable Long id) {
        mediaFolderService.delete(id);
        return R.ok();
    }

    @PostMapping("/upload")
    @OpLog(module = "media", action = "upload")
    @Operation(summary = "上传文件", description = "类型白名单 + 大小限制（默认 5MB），自动读取图片宽高")
    @Permit(PermissionCodes.MEDIA_UPLOAD)
    public R<Media> upload(@RequestPart("file") MultipartFile file,
                           @RequestParam(required = false, defaultValue = "") String folder,
                           @RequestParam(required = false) Long folderId) {
        return R.ok(mediaService.upload(file, folder, folderId));
    }

    @PostMapping("/upload/chunk/init")
    @Operation(summary = "分片上传 - 初始化", description = "返回 uploadId，客户端按序号依次上传分片")
    @Permit(PermissionCodes.MEDIA_UPLOAD)
    public R<String> initChunk(@RequestBody ChunkInitRequest request) {
        return R.ok(mediaService.initChunk(request.getUploadId(), request.getOriginalName()));
    }

    @PostMapping("/upload/chunk")
    @Operation(summary = "分片上传 - 写入分片")
    @Permit(PermissionCodes.MEDIA_UPLOAD)
    public R<Void> writeChunk(@RequestPart("file") MultipartFile file,
                              @RequestParam String uploadId,
                              @RequestParam int index) {
        mediaService.writeChunk(uploadId, index, file);
        return R.ok();
    }

    @PostMapping("/upload/chunk/merge")
    @OpLog(module = "media", action = "upload-chunk")
    @Operation(summary = "分片上传 - 合并")
    @Permit(PermissionCodes.MEDIA_UPLOAD)
    public R<Media> mergeChunk(@RequestBody ChunkMergeRequest request) {
        return R.ok(mediaService.mergeChunk(request.getUploadId(), request.getOriginalName(),
                request.getContentType(), request.getFolder(), request.getFolderId()));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "media", action = "delete")
    @Operation(summary = "删除媒体文件（同时删除磁盘文件）")
    @Permit(PermissionCodes.MEDIA_DELETE)
    public R<Void> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return R.ok();
    }

    @PostMapping("/batch/delete")
    @OpLog(module = "media", action = "batch-delete")
    @Operation(summary = "批量删除媒体文件")
    @Permit(PermissionCodes.MEDIA_DELETE)
    public R<Integer> batchDelete(@RequestBody BatchRequest request) {
        return R.ok(mediaService.delete(request.getIds()));
    }

    @Data
    public static class UpdateMediaRequest {
        /** 新名称，留空表示不改名 */
        private String name;
        /** 目标目录 ID：>0=指定目录，0=未分组，null=不移动 */
        private Long folderId;
    }

    @Data
    public static class ChunkInitRequest {
        private String uploadId;
        private String originalName;
    }

    @Data
    public static class FolderRequest {
        private String name;
        private Long parentId;
    }

    @Data
    public static class ChunkMergeRequest {
        private String uploadId;
        private String originalName;
        private String contentType;
        private String folder;
        private Long folderId;
    }

    @Data
    public static class BatchRequest {
        private List<Long> ids;
    }
}
