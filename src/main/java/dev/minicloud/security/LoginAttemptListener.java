package dev.minicloud.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
class LoginAttemptListener {

    private final LoginAttemptService loginAttempts;

    LoginAttemptListener(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    @EventListener
    void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        loginAttempts.onFailure(event.getAuthentication().getName());
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        loginAttempts.onSuccess(event.getAuthentication().getName());
    }
}
