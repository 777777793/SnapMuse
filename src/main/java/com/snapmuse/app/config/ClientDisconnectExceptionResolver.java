package com.snapmuse.app.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientDisconnectExceptionResolver implements HandlerExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientDisconnectExceptionResolver.class);

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            @Nullable Object handler,
            Exception ex) {
        if (!isClientDisconnect(ex)) {
            return null;
        }
        if (!response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
        log.debug("客户端连接已断开，忽略本次响应异常: uri={}", request.getRequestURI());
        return new ModelAndView();
    }

    private boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset by peer")
                        || normalized.contains("forcibly closed")
                        || normalized.contains("async request not usable")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
