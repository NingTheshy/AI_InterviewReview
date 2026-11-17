package com.interview.auth.config;

import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.RbacService;
import com.interview.common.constant.RoleCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("初始管理员引导测试")
class RbacBootstrapInitializerTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RbacService rbacService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private Environment environment;

    private RbacBootstrapInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new RbacBootstrapInitializer(userMapper, rbacService, passwordEncoder, environment);
        ReflectionTestUtils.setField(initializer, "username", "admin");
        ReflectionTestUtils.setField(initializer, "password", "123");
        ReflectionTestUtils.setField(initializer, "email", "admin@example.com");
        ReflectionTestUtils.setField(initializer, "nickname", "系统管理员");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
    }

    @Test
    @DisplayName("允许使用非空的简单初始密码")
    void run_simplePassword_createsBootstrapAdministrator() {
        when(rbacService.hasEffectivePermissionManager()).thenReturn(false);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("123")).thenReturn("encoded-password");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(passwordEncoder).encode("123");
        verify(rbacService).replaceUserRoles(null, 1L, Set.of(RoleCodes.USER, RoleCodes.ADMIN));
    }

    @Test
    @DisplayName("拒绝超过 BCrypt 上限的初始密码")
    void run_passwordLongerThanBcryptLimit_rejected() {
        ReflectionTestUtils.setField(initializer, "password", "a".repeat(73));
        when(rbacService.hasEffectivePermissionManager()).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments(new String[0])));

        verify(userMapper, never()).insert(any(User.class));
        verify(rbacService, never()).replaceUserRoles(any(), any(), any());
    }
}
