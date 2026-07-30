package com.settlement.tickle.global.auth.jwt.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JwtTokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String description;
}
