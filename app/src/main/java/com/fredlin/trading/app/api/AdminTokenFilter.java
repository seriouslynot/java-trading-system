package com.fredlin.trading.app.api;

import com.fredlin.trading.app.config.TradingProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminTokenFilter extends OncePerRequestFilter {
    private final byte[] expected;
    public AdminTokenFilter(TradingProperties props) { expected = props.adminToken().getBytes(StandardCharsets.UTF_8); }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();
        if (expected.length != 0 && "POST".equals(req.getMethod()) &&
                (path.startsWith("/api/ops/") || path.startsWith("/api/recon/"))) {
            var actual = req.getHeader("X-Admin-Token");
            if (actual == null || !MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
                res.setStatus(403);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"reason\":\"管理口令缺失或不正确\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
