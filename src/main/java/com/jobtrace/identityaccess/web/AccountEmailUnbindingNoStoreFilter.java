package com.jobtrace.identityaccess.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Makes all account-email command responses non-cacheable. */
@Component
public class AccountEmailUnbindingNoStoreFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AccountEmailUnbindingController.PATH.equals(request.getRequestURI())
                && !AccountEmailCodeController.PATH.equals(request.getRequestURI())
                && !RegistrationEmailCodeController.PATH.equals(request.getRequestURI())
                && !RegistrationController.PATH.equals(request.getRequestURI())
                && !LoginController.PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        filterChain.doFilter(request, response);
    }
}
