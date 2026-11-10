package com.qizixuan.heritage.security;

import com.qizixuan.heritage.entity.SysUser;
import com.qizixuan.heritage.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();
    private final SysUserMapper users;

    public TokenService(SysUserMapper users) {
        this.users = users;
    }

    public String create(SysUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenEntry(user.getId(), Instant.now().plus(TOKEN_TTL)));
        return token;
    }

    public SysUser find(String token) {
        if (token == null) return null;
        TokenEntry entry = tokens.get(token);
        if (entry == null) return null;
        if (entry.expiresAt().isBefore(Instant.now())) {
            tokens.remove(token);
            return null;
        }
        // 每次请求重载账号，确保停用和角色调整立即生效，不能沿用登录时的权限快照。
        SysUser user = users.selectById(entry.userId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            tokens.remove(token);
            return null;
        }
        return user;
    }

    public void remove(String token) { if (token != null) tokens.remove(token); }

    private record TokenEntry(Long userId, Instant expiresAt) {}
}
