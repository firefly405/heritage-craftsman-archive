package com.qizixuan.heritage;

import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.controller.AuthController;
import com.qizixuan.heritage.entity.SysUser;
import com.qizixuan.heritage.mapper.SysUserMapper;
import com.qizixuan.heritage.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationRoleTests {
    @Test
    void registrationAcceptsOnlyArtisanAndViewerRoles() {
        SysUserMapper users = mock(SysUserMapper.class);
        when(users.selectCount(any())).thenReturn(0L);
        AuthController controller = new AuthController(users, mock(TokenService.class), new BCryptPasswordEncoder());

        SysUser artisan = controller.register(new AuthController.RegisterRequest(
                "new_artisan", "secret123", "新艺人", "13800000000", "ARTISAN")).data();
        SysUser viewer = controller.register(new AuthController.RegisterRequest(
                "new_viewer", "secret123", "新用户", null, "VIEWER")).data();

        assertEquals("ARTISAN", artisan.getRole());
        assertEquals("VIEWER", viewer.getRole());
        assertThrows(BusinessException.class, () -> controller.register(new AuthController.RegisterRequest(
                "fake_admin", "secret123", "伪造管理员", null, "ADMIN")));
        assertThrows(BusinessException.class, () -> controller.register(new AuthController.RegisterRequest(
                "bad_phone", "secret123", "错误手机号", "12800000000", "VIEWER")));
        verify(users, org.mockito.Mockito.times(2)).insert(any(SysUser.class));
    }
}
