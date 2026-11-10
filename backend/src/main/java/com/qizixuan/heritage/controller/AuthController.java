package com.qizixuan.heritage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.PhoneNumbers;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.SysUser;
import com.qizixuan.heritage.mapper.SysUserMapper;
import com.qizixuan.heritage.security.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final SysUserMapper users;
    private final TokenService tokens;
    private final BCryptPasswordEncoder encoder;
    public AuthController(SysUserMapper users, TokenService tokens, BCryptPasswordEncoder encoder) {
        this.users = users; this.tokens = tokens; this.encoder = encoder;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.username().trim()));
        if (user == null || !encoder.matches(request.password(), user.getPassword())) throw new BusinessException("用户名或密码错误");
        if (!Boolean.TRUE.equals(user.getEnabled())) throw new BusinessException("账号已被停用");
        return ApiResponse.ok(Map.of("token", tokens.create(user), "user", user));
    }

    @PostMapping("/register")
    public ApiResponse<SysUser> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.username().trim();
        String phone = PhoneNumbers.normalize(request.phone());
        String role = request.role() == null ? "" : request.role().trim().toUpperCase(Locale.ROOT);
        if (!"ARTISAN".equals(role) && !"VIEWER".equals(role))
            throw new BusinessException("请选择非遗艺人或普通用户");
        if (users.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0)
            throw new BusinessException("用户名已存在");
        SysUser user = new SysUser();
        user.setUsername(username); user.setPassword(encoder.encode(request.password()));
        user.setRealName(request.realName().trim());
        user.setPhone(phone == null || phone.isEmpty() ? null : phone); user.setRole(role);
        user.setEnabled(true); user.setCreatedAt(LocalDateTime.now()); users.insert(user);
        return ApiResponse.ok(user);
    }

    @GetMapping("/me")
    public ApiResponse<SysUser> me() { return ApiResponse.ok(UserSession.get()); }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        tokens.remove(header != null && header.startsWith("Bearer ") ? header.substring(7) : null);
        return ApiResponse.ok();
    }

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 100) String password) {}
    public record RegisterRequest(@NotBlank @Size(min = 3, max = 50) String username,
                                  @NotBlank @Size(min = 6, max = 72) String password,
                                  @NotBlank @Size(max = 50) String realName,
                                  @Size(max = 30) String phone,
                                  @NotBlank @Size(max = 20) String role) {}
}
