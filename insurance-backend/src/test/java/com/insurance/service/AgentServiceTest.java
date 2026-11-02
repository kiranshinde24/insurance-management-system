package com.insurance.service;

import com.insurance.dto.AgentDTO;
import com.insurance.exception.BadRequestException;
import com.insurance.exception.ConflictException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.Agent;
import com.insurance.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock private AgentRepository repo;
    @Mock private PasswordEncoder encoder;

    private AgentService service;

    @BeforeEach
    void setUp() {
        service = new AgentService(repo, encoder);
    }

    private AgentDTO validDto() {
        return AgentDTO.builder()
                .name("Agent Smith").username("asmith").password("secret123")
                .phone("9999999999").email("agent@example.com").comm(5.0)
                .build();
    }

    @Test
    @DisplayName("create rejects an agent without a name")
    void create_missingName_throwsBadRequest() {
        AgentDTO dto = validDto();
        dto.setName("  ");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Agent name is required");
    }

    @Test
    @DisplayName("create rejects an agent without a commission value")
    void create_missingCommission_throwsBadRequest() {
        AgentDTO dto = validDto();
        dto.setComm(null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Commission is required");
    }

    @Test
    @DisplayName("create rejects a duplicate username")
    void create_duplicateUsername_throwsConflict() {
        AgentDTO dto = validDto();
        when(repo.findByUsername("asmith")).thenReturn(Optional.of(Agent.builder().id(9).username("asmith").build()));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already exists");

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("create hashes the password and saves a new agent")
    void create_validAgent_savesWithHashedPassword() {
        AgentDTO dto = validDto();
        when(repo.findByUsername("asmith")).thenReturn(Optional.empty());
        when(encoder.encode("secret123")).thenReturn("hashed-pw");
        when(repo.save(any(Agent.class))).thenAnswer(inv -> {
            Agent a = inv.getArgument(0);
            a.setId(1);
            return a;
        });

        AgentDTO saved = service.create(dto);

        assertThat(saved.getId()).isEqualTo(1);
        assertThat(saved.getPassword()).isNull(); // password is never returned
        verify(repo).save(argThat(a -> "hashed-pw".equals(a.getPassword())));
    }

    @Test
    @DisplayName("update allows keeping the same username for the same agent")
    void update_sameUsernameSameAgent_doesNotConflict() {
        Agent existing = Agent.builder().id(1).name("Old").username("asmith").password("old-hash").build();
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.findByUsername("asmith")).thenReturn(Optional.of(existing));
        when(repo.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentDTO dto = validDto();
        dto.setPassword(null); // no password change

        AgentDTO result = service.update(1, dto);

        assertThat(result.getName()).isEqualTo("Agent Smith");
        verify(encoder, never()).encode(any());
    }

    @Test
    @DisplayName("update rejects a username already used by a different agent")
    void update_usernameTakenByAnotherAgent_throwsConflict() {
        Agent existing = Agent.builder().id(1).name("Old").username("oldname").password("old-hash").build();
        Agent other = Agent.builder().id(2).username("asmith").build();
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.findByUsername("asmith")).thenReturn(Optional.of(other));

        AgentDTO dto = validDto();

        assertThatThrownBy(() -> service.update(1, dto))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("update only re-hashes the password when a new one is supplied")
    void update_withNewPassword_reHashesPassword() {
        Agent existing = Agent.builder().id(1).name("Old").username("asmith").password("old-hash").build();
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.findByUsername("asmith")).thenReturn(Optional.of(existing));
        when(encoder.encode("newpass123")).thenReturn("new-hash");
        when(repo.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentDTO dto = validDto();
        dto.setPassword("newpass123");

        service.update(1, dto);

        verify(repo).save(argThat(a -> "new-hash".equals(a.getPassword())));
    }

    @Test
    @DisplayName("update throws ResourceNotFoundException for an unknown agent")
    void update_unknownAgent_throws() {
        when(repo.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404, validDto()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getById throws ResourceNotFoundException for an unknown agent")
    void getById_unknownAgent_throws() {
        when(repo.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(404))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
