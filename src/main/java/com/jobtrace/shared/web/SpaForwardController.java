package com.jobtrace.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SpaForwardController {

    public static final String BROWSER_ROUTE = "/{path:^(?!api$|actuator$|assets$)[^.]*$}";
    public static final String NESTED_BROWSER_ROUTE = BROWSER_ROUTE + "/**";

    @GetMapping(
            value = {BROWSER_ROUTE, NESTED_BROWSER_ROUTE},
            produces = MediaType.TEXT_HTML_VALUE)
    public String forwardBrowserRoute(HttpServletRequest request) {
        if (request.getRequestURI().contains(".")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }
}
