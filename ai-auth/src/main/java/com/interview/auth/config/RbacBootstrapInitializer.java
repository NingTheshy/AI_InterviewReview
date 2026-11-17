package com.interview.auth.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.RbacService;
import com.interview.common.constant.RoleCodes;
import com.interview.common.constant.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;

/**
 * 在系统没有有效权限管理者时，从环境变量安全引导创建首个管理员。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacBootstrapInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final RbacService rbacService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${interview.security.bootstrap-admin.username:}")
    private String username;
    @Value("${interview.security.bootstrap-admin.password:}")
    private String password;
    @Value("${interview.security.bootstrap-admin.email:}")
    private String email;
    @Value("${interview.security.bootstrap-admin.nickname:系统管理员}")
    private String nickname;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (rbacService.hasEffectivePermissionManager()) {
            return;
        }
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);
        if (isBlank(username) || isBlank(password) || isBlank(email)) {
            if (production) {
                throw new IllegalStateException("系统尚无权限管理者，生产环境必须配置 RBAC_BOOTSTRAP_ADMIN_USERNAME、PASSWORD 和 EMAIL");
            }
            log.warn("系统尚无权限管理者；开发环境可通过 RBAC_BOOTSTRAP_ADMIN_* 配置创建初始管理员");
            return;
        }
        validatePassword(password);
        long duplicate = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .or()
                .eq(User::getEmail, email));
        if (duplicate > 0) {
            throw new IllegalStateException("初始管理员用户名或邮箱已存在，请更换配置或手工恢复管理权限");
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setNickname(isBlank(nickname) ? username.trim() : nickname.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE.getCode());
        user.setLoginCount(0);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);
        rbacService.replaceUserRoles(null, user.getId(), Set.of(RoleCodes.USER, RoleCodes.ADMIN));
        log.info("已创建初始权限管理者，userId={}", user.getId());
    }

    private void validatePassword(String value) {
        // 是否为空已在引导配置完整性检查中处理；这里只限制 BCrypt 的安全输入上限。
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("初始管理员密码不能超过 72 字节");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
