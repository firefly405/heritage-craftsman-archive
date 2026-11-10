package com.qizixuan.heritage.common;

import com.qizixuan.heritage.entity.SysUser;

public final class UserSession {
    private static final ThreadLocal<SysUser> CURRENT = new ThreadLocal<>();
    private UserSession() {}
    public static void set(SysUser user) { CURRENT.set(user); }
    public static SysUser get() { return CURRENT.get(); }
    public static Long id() { return CURRENT.get() == null ? null : CURRENT.get().getId(); }
    public static void clear() { CURRENT.remove(); }
}
