package tech.lemnova.continuum_backend.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import tech.lemnova.continuum_backend.user.User;
import tech.lemnova.continuum_backend.user.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
        throws UsernameNotFoundException {
        User user = userRepository
            .findByUsername(username)
            .orElseThrow(() ->
                new UsernameNotFoundException("User not found: " + username)
            );

        return new CustomUserDetails(user);
    }

    public UserDetails loadUserById(String userId)
        throws UsernameNotFoundException {
        User user = userRepository
            .findById(userId)
            .orElseThrow(() ->
                new UsernameNotFoundException(
                    "User not found with id: " + userId
                )
            );

        return new CustomUserDetails(user);
    }
}
