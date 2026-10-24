package com.jobtrace.identityaccess;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.UploadAvatar;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AvatarControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private UploadAvatar upload;
    private final MockMultipartFile image = new MockMultipartFile("file", "x.png", "image/png",
            new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/profile/avatar").file(image).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifiedOwnerReceivesOnlyTheirUploadUrl() throws Exception {
        when(upload.execute(eq("owner"), any(UploadAvatar.Input.class)))
                .thenReturn("https://images.test/avatars/owner/123.png");
        var authentication = new UsernamePasswordAuthenticationToken("owner", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(new BridgeIdentity("owner", BridgeIdentity.Role.USER, 3));
        mockMvc.perform(multipart("/api/profile/avatar").file(image).with(csrf())
                .with(SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url")
                        .value("https://images.test/avatars/owner/123.png"));
        verify(upload).execute(eq("owner"), any(UploadAvatar.Input.class));
    }
}
