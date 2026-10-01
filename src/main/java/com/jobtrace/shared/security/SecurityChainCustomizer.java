package com.jobtrace.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/** Optional module hook for contributing narrowly scoped security filters. */
@FunctionalInterface
public interface SecurityChainCustomizer {

    void customize(HttpSecurity http) throws Exception;
}
