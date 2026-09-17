package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.entity.Notice;
import com.hz.delivery.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ApiResult<List<Notice>> list() {
        return ApiResult.ok(noticeService.listAdmin());
    }

    @PostMapping
    public ApiResult<Notice> save(@RequestBody Notice notice) {
        return ApiResult.ok(noticeService.save(notice));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return ApiResult.ok();
    }
}
