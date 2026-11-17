package com.interview.auth.service.impl;

import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.AvatarStorageService;
import com.interview.auth.service.RbacService;
import com.interview.common.exception.BusinessException;
import com.interview.common.utils.JwtUtil;
import com.interview.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvatarUploadServiceTest {

    @Mock UserMapper userMapper;
    @Mock RedisUtil redisUtil;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock RbacService rbacService;
    @Mock AvatarStorageService avatarStorageService;
    @InjectMocks AuthServiceImpl service;

    private final MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});
    private static final String OLD = "/avatars/11111111111111111111111111111111.png";
    private static final String NEW = "/avatars/22222222222222222222222222222222.png";

    @Test
    void replacesAvatarAndDeletesPreviousFileAfterSuccessfulUpdate() {
        User user = new User();
        user.setId(1L);
        user.setAvatar(OLD);
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(user);
        when(avatarStorageService.store(file)).thenReturn(NEW);
        when(userMapper.updateById(user)).thenReturn(1);

        service.uploadAvatar(1L, file);

        assertEquals(NEW, user.getAvatar());
        verify(avatarStorageService).deleteStoredAvatar(OLD);
        verify(avatarStorageService, never()).deleteStoredAvatar(NEW);
    }

    @Test
    void updateFailureRemovesNewFileButPreservesPreviousFile() {
        User user = new User();
        user.setId(1L);
        user.setAvatar(OLD);
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(user);
        when(avatarStorageService.store(file)).thenReturn(NEW);
        when(userMapper.updateById(user)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.uploadAvatar(1L, file));

        verify(avatarStorageService).deleteStoredAvatar(NEW);
        verify(avatarStorageService, never()).deleteStoredAvatar(OLD);
    }

    @Test
    void unknownUserCannotUploadFile() {
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.uploadAvatar(1L, file));
        verifyNoInteractions(avatarStorageService);
    }

    @Test
    void transactionRollbackRemovesNewFileAndRetainsOldFile() {
        User user = new User();
        user.setId(1L);
        user.setAvatar(OLD);
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(user);
        when(avatarStorageService.store(file)).thenReturn(NEW);
        when(userMapper.updateById(user)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.uploadAvatar(1L, file);
            verify(avatarStorageService, never()).deleteStoredAvatar(OLD);
            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(avatarStorageService).deleteStoredAvatar(NEW);
            verify(avatarStorageService, never()).deleteStoredAvatar(OLD);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
