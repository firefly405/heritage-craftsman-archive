package com.qizixuan.heritage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.SysUser;
import com.qizixuan.heritage.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final SysUserMapper mapper;
    public UserController(SysUserMapper mapper) { this.mapper = mapper; }
    private void admin() { if (!"ADMIN".equals(UserSession.get().getRole())) throw new BusinessException("仅管理员可执行此操作"); }

    @GetMapping
    public ApiResponse<List<SysUser>> list(@RequestParam(defaultValue = "") String keyword) {
        admin();
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getCreatedAt);
        if (!keyword.isBlank()) q.and(x -> x.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword));
        return ApiResponse.ok(mapper.selectList(q));
    }
    @PutMapping("/{id}/status")
    public ApiResponse<Void> status(@PathVariable Long id, @RequestBody StatusRequest request) {
        admin(); SysUser user = mapper.selectById(id); if (user == null) throw new BusinessException("用户不存在");
        if (request == null || request.enabled() == null) throw new BusinessException("账号状态不能为空");
        if (id.equals(UserSession.id()) && !request.enabled()) throw new BusinessException("不能停用当前登录账号");
        user.setEnabled(request.enabled()); mapper.updateById(user); return ApiResponse.ok();
    }
    @PutMapping("/{id}/role")
    public ApiResponse<Void> role(@PathVariable Long id, @RequestBody RoleRequest request) {
        admin(); SysUser user = mapper.selectById(id); if (user == null) throw new BusinessException("用户不存在");
        if (request == null || request.role() == null || !List.of("ADMIN", "REVIEWER", "ARTISAN", "VIEWER").contains(request.role())) throw new BusinessException("角色无效");
        if (id.equals(UserSession.id()) && !"ADMIN".equals(request.role())) throw new BusinessException("不能取消当前登录账号的管理员角色");
        user.setRole(request.role()); mapper.updateById(user); return ApiResponse.ok();
    }
    public record StatusRequest(Boolean enabled) {}
    public record RoleRequest(String role) {}
}
