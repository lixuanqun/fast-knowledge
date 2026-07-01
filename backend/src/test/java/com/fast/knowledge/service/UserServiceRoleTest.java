package com.fast.knowledge.service;

import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.dto.CreateUserRequest;
import com.fast.knowledge.model.dto.UpdateUserRequest;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.model.vo.UserVO;
import com.fast.knowledge.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRoleTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setAdminContext() {
        UserContext ctx = new UserContext();
        ctx.setUserId(1L);
        ctx.setUsername("admin");
        ctx.setRole("ADMIN");
        UserContext.set(ctx);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void createUserNormalizesUserRole() {
        when(userMapper.findByUsername("bob")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        doAnswer(inv -> {
            KbUser u = inv.getArgument(0);
            u.setId(2L);
            return null;
        }).when(userMapper).insert(any());
        when(userMapper.findById(2L)).thenAnswer(inv -> {
            KbUser u = new KbUser();
            u.setId(2L);
            u.setUsername("bob");
            u.setRole("USER");
            return u;
        });

        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("bob");
        req.setPassword("pass123");
        req.setRole("USER");

        UserVO vo = userService.create(req);
        assertEquals("USER", vo.getRole());

        ArgumentCaptor<KbUser> captor = ArgumentCaptor.forClass(KbUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("USER", captor.getValue().getRole());
    }

    @Test
    void createUserMapsUnknownRoleToUser() {
        when(userMapper.findByUsername("carol")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        doAnswer(inv -> {
            KbUser u = inv.getArgument(0);
            u.setId(3L);
            return null;
        }).when(userMapper).insert(any());
        when(userMapper.findById(3L)).thenAnswer(inv -> {
            KbUser u = new KbUser();
            u.setId(3L);
            u.setUsername("carol");
            u.setRole("USER");
            return u;
        });

        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("carol");
        req.setPassword("pass123");
        req.setRole("EDITOR");

        userService.create(req);

        ArgumentCaptor<KbUser> captor = ArgumentCaptor.forClass(KbUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("USER", captor.getValue().getRole());
    }

    @Test
    void updateUserNormalizesRole() {
        KbUser existing = new KbUser();
        existing.setId(5L);
        existing.setUsername("dave");
        existing.setRole("USER");
        when(userMapper.findById(5L)).thenReturn(existing, existing);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setRole("ADMIN");

        userService.update(5L, req);

        ArgumentCaptor<KbUser> captor = ArgumentCaptor.forClass(KbUser.class);
        verify(userMapper).update(captor.capture());
        assertEquals("ADMIN", captor.getValue().getRole());
    }
}
