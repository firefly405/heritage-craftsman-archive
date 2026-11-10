package com.qizixuan.heritage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.Announcement;
import com.qizixuan.heritage.mapper.AnnouncementMapper;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementMapper mapper;
    public AnnouncementController(AnnouncementMapper mapper) { this.mapper = mapper; }
    @GetMapping public ApiResponse<List<Announcement>> list(@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean published) {
        LambdaQueryWrapper<Announcement> query = new LambdaQueryWrapper<Announcement>().orderByDesc(Announcement::getCreatedAt);
        if (!isAdmin()) query.eq(Announcement::getPublished, true);
        else if(published!=null) query.eq(Announcement::getPublished,published);
        if(!keyword.isBlank())query.like(Announcement::getTitle,keyword.trim());
        return ApiResponse.ok(mapper.selectList(query));
    }
    @GetMapping("/{id}") public ApiResponse<Announcement> get(@PathVariable Long id){
        Announcement item=mapper.selectById(id);
        if(item==null || (!isAdmin() && !Boolean.TRUE.equals(item.getPublished())))throw new BusinessException("公告不存在或尚未发布");
        return ApiResponse.ok(item);
    }
    @PostMapping public ApiResponse<Announcement> create(@RequestBody Announcement item) {
        requireAdmin(); validate(item);
        item.setId(null); item.setPublisher(UserSession.get().getRealName()); item.setCreatedAt(LocalDateTime.now());
        if (item.getPublished() == null) item.setPublished(true); mapper.insert(item); return ApiResponse.ok(item);
    }
    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable Long id, @RequestBody Announcement item) {
        requireAdmin(); validate(item);
        Announcement current = mapper.selectById(id);
        if (current == null) throw new BusinessException("公告不存在");
        item.setId(id); item.setPublisher(current.getPublisher()); item.setCreatedAt(current.getCreatedAt());
        if (item.getPublished() == null) item.setPublished(current.getPublished());
        mapper.updateById(item); return ApiResponse.ok();
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) {
        requireAdmin();
        if (mapper.deleteById(id) == 0) throw new BusinessException("公告不存在");
        return ApiResponse.ok();
    }
    private boolean isAdmin() { return UserSession.get() != null && "ADMIN".equals(UserSession.get().getRole()); }
    private void requireAdmin() { if (!isAdmin()) throw new BusinessException("仅管理员可维护通知公告"); }
    private void validate(Announcement item) {
        if (item == null || item.getTitle() == null || item.getTitle().isBlank()) throw new BusinessException("公告标题不能为空");
        if (item.getContent() == null || item.getContent().isBlank()) throw new BusinessException("公告内容不能为空");
        if(item.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65000)throw new BusinessException("公告内容不能超过65000字节");
        item.setTitle(item.getTitle().trim());
        if (item.getTitle().length() > 150) throw new BusinessException("公告标题不能超过150个字符");
    }
}
