package com.interview.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.auth.dto.*;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.AuthService;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.UserStatus;
import com.interview.common.exception.BusinessException;
import com.interview.common.utils.JwtUtil;
import com.interview.common.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现类
 * <p>
 * 提供用户注册、登录、退出、个人信息管理等认证相关功能。
 * 使用 Redis 进行验证码缓存和 Token 黑名单管理。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Redis Key 前缀：邮箱验证码
     * 格式：auth:verify_code:{email}
     * 过期时间：5 分钟
     */
    private static final String VERIFY_CODE_PREFIX = "auth:verify_code:";

    /**
     * Redis Key 前缀：验证码发送限流
     * 格式：auth:verify_code_limit:{email}
     * 过期时间：60 秒
     */
    private static final String VERIFY_CODE_LIMIT_PREFIX = "auth:verify_code_limit:";

    /**
     * Redis Key 前缀：Token 黑名单
     * 格式：token:blacklist:{jti}
     * 过期时间：Token 剩余有效期
     */
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * Redis Key 前缀：登录失败次数
     * 格式：auth:login_fail:{account}
     * 过期时间：15 分钟
     */
    private static final String LOGIN_FAIL_PREFIX = "auth:login_fail:";

    /**
     * 验证码有效期（分钟）
     */
    private static final long VERIFY_CODE_EXPIRE_MINUTES = 5;

    /**
     * 验证码发送限流时间（秒）
     */
    private static final long VERIFY_CODE_LIMIT_SECONDS = 60;

    /**
     * 登录失败次数上限
     */
    private static final int LOGIN_FAIL_LIMIT = 5;

    /**
     * 登录失败锁定时间（分钟）
     */
    private static final long LOGIN_FAIL_LOCK_MINUTES = 15;

    /**
     * 发送邮箱验证码
     * <p>
     * 生成 6 位数字验证码，缓存到 Redis（5分钟有效期）。
     * 同一邮箱 60 秒内只能发送一次（限流）。
     * </p>
     *
     * @param email 邮箱地址
     */
    @Override
    public void sendVerifyCode(String email) {
        // 检查发送频率限制（60秒内不能重复发送）
        String limitKey = VERIFY_CODE_LIMIT_PREFIX + email;
        if (Boolean.TRUE.equals(redisUtil.hasKey(limitKey))) {
            throw new BusinessException(ErrorCode.VERIFY_CODE_FREQUENT.getCode(),
                    ErrorCode.VERIFY_CODE_FREQUENT.getMessage());
        }

        // 生成 6 位随机验证码
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        // 缓存验证码到 Redis（5分钟有效期）
        String codeKey = VERIFY_CODE_PREFIX + email;
        redisUtil.set(codeKey, code, VERIFY_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 设置发送限流（60秒）
        redisUtil.set(limitKey, "1", VERIFY_CODE_LIMIT_SECONDS, TimeUnit.SECONDS);

        // TODO: 实际项目中应调用邮件服务发送验证码
        // 这里仅打印日志，开发环境可从控制台获取验证码
        log.info("验证码已生成，邮箱: {}, 验证码: {} (开发环境日志)", email, code);
    }

    /**
     * 用户注册
     * <p>
     * 验证邮箱验证码，检查用户名和邮箱唯一性，
     * 创建新用户。
     * </p>
     *
     * @param request 注册请求（邮箱、用户名、密码、验证码）
     */
    @Override
    public void register(RegisterRequest request) {
        String email = request.getEmail();
        String username = request.getUsername();
        String password = request.getPassword();
        String code = request.getCode();

        // 1. 验证邮箱验证码
        String codeKey = VERIFY_CODE_PREFIX + email;
        String cachedCode = redisUtil.get(codeKey);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new BusinessException(ErrorCode.VERIFY_CODE_ERROR.getCode(),
                    ErrorCode.VERIFY_CODE_ERROR.getMessage());
        }

        // 验证通过后删除验证码
        redisUtil.delete(codeKey);

        // 2. 检查邮箱是否已被注册
        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(User::getEmail, email);
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS.getCode(),
                    ErrorCode.EMAIL_EXISTS.getMessage());
        }

        // 3. 检查用户名是否已被使用
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, username);
        if (userMapper.selectCount(usernameWrapper) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS.getCode(),
                    ErrorCode.USERNAME_EXISTS.getMessage());
        }

        // 4. 创建新用户
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);
        user.setRole(0); // 0 = user, 1 = admin
        user.setStatus(UserStatus.ACTIVE.getCode());
        user.setLoginCount(0);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);

        log.info("用户注册成功: {}", username);
    }

    /**
     * 用户登录
     * <p>
     * 支持邮箱或用户名登录，验证密码，
     * 检查账户状态，生成 JWT Token。
     * </p>
     *
     * @param request 登录请求（账号、密码）
     * @return 登录响应（包含 Token）
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        String account = request.getAccount();
        String password = request.getPassword();

        // 检查登录失败次数（防止暴力破解）
        String failKey = LOGIN_FAIL_PREFIX + account;
        String failCountStr = redisUtil.get(failKey);
        if (failCountStr != null) {
            int failCount = Integer.parseInt(failCountStr);
            if (failCount >= LOGIN_FAIL_LIMIT) {
                throw new BusinessException(ErrorCode.LOGIN_FAILED.getCode(),
                        ErrorCode.LOGIN_FAILED.getMessage());
            }
        }

        // 1. 根据账号（邮箱或用户名）查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(User::getEmail, account).or().eq(User::getUsername, account));
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            incrementLoginFailCount(account);
            throw new BusinessException(ErrorCode.PASSWORD_ERROR.getCode(),
                    ErrorCode.PASSWORD_ERROR.getMessage());
        }

        // 2. 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            incrementLoginFailCount(account);
            throw new BusinessException(ErrorCode.PASSWORD_ERROR.getCode(),
                    ErrorCode.PASSWORD_ERROR.getMessage());
        }

        // 3. 检查账户状态
        if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLED.getCode()) {
            throw new BusinessException(ErrorCode.USER_DISABLED.getCode(),
                    ErrorCode.USER_DISABLED.getMessage());
        }

        // 4. 清除登录失败记录
        redisUtil.delete(failKey);

        // 5. 更新登录信息
        user.setLastLoginAt(LocalDateTime.now());
        user.setLoginCount((user.getLoginCount() != null ? user.getLoginCount() : 0) + 1);
//      user.setLoginCount(user.getLoginCount() + 1);
        userMapper.updateById(user);

        log.info("用户登录成功: {}", user.getUsername());

        // 6. 生成登录响应
        return generateLoginResponse(user);
    }

    /**
     * 退出登录
     * <p>
     * 将当前 Token 的 JTI 加入 Redis 黑名单，
     * 使 Token 在剩余有效期内失效。
     * </p>
     *
     * @param token JWT Token（从请求头获取）
     */
    @Override
    public void logout(String token) {
        invalidateToken(token);
    }

    /**
     * 获取当前用户信息
     *
     * @param userId 用户 ID（从 JWT Token 中解析）
     * @return 用户资料响应
     */
    @Override
    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        UserProfileResponse response = UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();
        return response;
    }

    /**
     * 修改密码
     * <p>
     * 验证旧密码后更新为新密码，并将当前 Token 加入黑名单以确保安全。
     * </p>
     *
     * @param userId  用户 ID
     * @param request 修改密码请求（旧密码、新密码）
     * @param token   当前有效的 JWT Token（用于失效处理）
     */
    @Override
    public void changePassword(Long userId, ChangePasswordRequest request, String token) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR.getCode(),
                    ErrorCode.PASSWORD_ERROR.getMessage());
        }

        // 检查新密码不能与旧密码相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                    "新密码不能与旧密码相同");
        }

        // 更新为新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);

        // 将当前 Token 加入黑名单，防止修改密码后旧 Token 仍有效
        invalidateToken(token);

        log.info("用户修改密码成功，旧 Token 已失效: {}", user.getUsername());
    }

    /**
     * 更新用户信息
     * <p>
     * 更新昵称和头像（仅允许修改非空字段）。
     * </p>
     *
     * @param userId  用户 ID
     * @param request 更新请求（昵称、头像）
     */
    @Override
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        userMapper.updateById(user);
        log.info("用户信息更新成功: {}", user.getUsername());
    }

    /**
     * 增加登录失败次数
     *
     * @param account 账号（邮箱或用户名）
     */
    private void incrementLoginFailCount(String account) {
        String failKey = LOGIN_FAIL_PREFIX + account;
        Long failCount = redisUtil.increment(failKey);
        if (failCount != null && failCount == 1) {
            // 首次失败，设置过期时间
            redisUtil.expire(failKey, LOGIN_FAIL_LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * 生成登录响应
     * <p>
     * 生成 JWT Token 并构建登录响应对象。
     * </p>
     *
     * @param user 用户实体
     * @return 登录响应（包含 Token 和用户信息）
     */
    private LoginResponse generateLoginResponse(User user) {
        // 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 构建响应
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(604800L) // 7天（秒）
                .user(userInfo)
                .build();

        return response;
    }

    /**
     * 将指定 Token 加入 Redis 黑名单（使其立即失效）
     *
     * @param token JWT Token
     */
    private void invalidateToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            String jti = jwtUtil.extractJti(token);
            long expiration = jwtUtil.getExpiration(token);
            long remainingTime = expiration - System.currentTimeMillis();

            if (remainingTime > 0) {
                String blacklistKey = TOKEN_BLACKLIST_PREFIX + jti;
                redisUtil.set(blacklistKey, "1", remainingTime, TimeUnit.MILLISECONDS);
                log.info("Token 已加入黑名单: {}", jti);
            }
        } catch (Exception e) {
            log.warn("处理 Token 失败: {}", e.getMessage());
        }
    }
}
