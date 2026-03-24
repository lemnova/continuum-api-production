package tech.lemnova.continuum.infra.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.SendEmailRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final Resend resend;

    @Value("${email.from}")
    private String from;

    @Value("${app.url}")
    private String appUrl;

    public EmailService(@Value("${resend.api-key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    public void sendVerificationEmail(String to, String token) {
        String link = appUrl + "/verify-email?token=" + token;
        try {
            resend.emails().send(SendEmailRequest.builder()
                    .from(from).to(to)
                    .subject("Verify your Continuum account")
                    .html(buildVerificationHtml(link))
                    .build());
            log.info("Verification email sent to {}", to);
        } catch (ResendException e) {
            log.error("Failed to send verification email to {}: {}", to, e.getMessage());
            throw new IllegalStateException("Email service unavailable");
        }
    }

    public void sendPasswordResetEmail(String to, String token) {
        // Simple template: can be improved
        String body = "Reset your password using the following token: " + token;
        // re-use resend client if available
        try { send(to, "Reset your Continuum password", body); }
        catch (Exception e) { throw e; }
    }

    public void sendEmailChangeVerification(String to, String token) {
        String body = "Confirm your new email by visiting the verification endpoint with this token: " + token;
        try { send(to, "Confirm your new Continuum email", body); }
        catch (Exception e) { throw e; }
    }

    private void send(String to, String subject, String body) {
        try {
            resend.emails().send(SendEmailRequest.builder()
                    .from(from).to(to)
                    .subject(subject)
                    .html(body)
                    .build());
            log.info("Email sent to {} subject={}", to, subject);
        } catch (ResendException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new IllegalStateException("Email service unavailable");
        }
    }

    private String buildVerificationHtml(String link) {
        return """
            <html><body style="font-family:Arial;background:#f5f5f5;padding:40px;">
              <div style="max-width:480px;margin:0 auto;background:#fff;padding:32px;border-radius:8px;">
                <h2 style="color:#1A1A2E;">Welcome to Continuum 👋</h2>
                <p>Click below to verify your email address.</p>
                <div style="text-align:center;margin:28px 0;">
                  <a href="%s" style="padding:14px 28px;background:#E94560;color:#fff;
                     text-decoration:none;border-radius:6px;font-weight:bold;">Verify Email</a>
                </div>
                <p style="color:#666;font-size:13px;">Link: %s</p>
              </div>
            </body></html>""".formatted(link, link);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INFRA — stripe
// ─────────────────────────────────────────────────────────────────────────────
