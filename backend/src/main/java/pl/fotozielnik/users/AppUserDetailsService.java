package pl.fotozielnik.users;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks users up by email for sign-in. The resulting principal's name is the user's <b>id</b>, not the email:
 * ids never change, and every feature reads the owner from it ({@code shared.security.CurrentUser}). The
 * principal is a plain Spring {@link User}, so sessions stored in the database survive our code changes.
 */
@Service
class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser user = users.findByEmail(AppUser.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("unknown email"));
        return User.withUsername(user.getId().toString())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
