package com.settlement.tickle.global.auth.custom;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
@RequiredArgsConstructor
public class CustomUserPrincipal {

    private final Long userId;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;

}
