package com.iot.platform.security;

import com.iot.platform.system.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 登录用户信息
 */
@Data
@AllArgsConstructor
public class LoginUser implements UserDetails {

    private SysUser user;
    private List<String> roles;
    private List<String> permissions;
    private Long tenantId;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null) return Collections.emptyList();
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
    }

    @Override public String getPassword() { return user == null ? null : user.getPassword(); }
    @Override public String getUsername() { return user == null ? null : user.getUsername(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return user != null && Integer.valueOf(1).equals(user.getStatus()); }
}
