package com.portfolioguard.portfolioguard.security;

import com.portfolioguard.portfolioguard.model.User;
import com.portfolioguard.portfolioguard.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks CustomUserDetailsService service;

    @Test
    void loadUserByUsername_found_returnsUserPrincipal() {
        User user = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("alice");

        assertThat(result).isInstanceOf(UserPrincipal.class);
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void loadUserByUsername_notFound_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void loadUserById_found_returnsUserPrincipal() {
        User user = user("u1", "alice");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserById("u1");

        assertThat(result).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) result).getId()).isEqualTo("u1");
    }

    @Test
    void loadUserById_notFound_throwsUsernameNotFoundException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserById("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ------------------------------------------------------------------
    // UserPrincipal delegation tests — cover the boolean status methods
    // ------------------------------------------------------------------

    @Test
    void userPrincipal_accountStatusMethodsReturnTrue() {
        User user = user("u1", "alice");
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    void userPrincipal_getPassword_returnsPasswordHash() {
        User user = user("u1", "alice");
        user.setPasswordHash("hashed-pw");
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getPassword()).isEqualTo("hashed-pw");
    }

    @Test
    void userPrincipal_getAuthorities_returnsRoleAuthority() {
        User user = user("u1", "alice");
        user.setRole("ANALYST");
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ANALYST"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private User user(String id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setRole("USER");
        return u;
    }
}
