package tech.lemnova.continuum_backend.auth;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tech.lemnova.continuum_backend.subscription.PlanType;
import tech.lemnova.continuum_backend.user.User;

public class CustomUserDetails implements UserDetails {

    private final String userId;
    private final String username;
    private final String password;
    private final String email;
    private final boolean active;
    private final String role; // ✅ Role real (USER/ADMIN)
    private final PlanType planType;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.email = user.getEmail();
        this.active = user.getActive();
        this.role = user.getRole(); // ✅ Pegando role real
        this.planType = user.getPlanType();

        // ✅ CORRIGIDO: Role baseado em User.role, não em planType
        this.authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
        );
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public PlanType getPlanType() {
        return planType;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active; // ✅ Já valida se está ativo
    }
}
