package tech.lemnova.continuum.infra.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.lemnova.continuum.application.exception.BadRequestException;

import java.util.Collections;

@Service
public class GoogleOAuthService {

    @Value("${google.oauth.client-id}")
    private String clientId;

    public GoogleUserInfo verify(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId)).build();

            GoogleIdToken token = verifier.verify(idTokenString);
            if (token == null) throw new BadRequestException("Invalid Google token");

            GoogleIdToken.Payload p = token.getPayload();
            return new GoogleUserInfo(p.getSubject(), p.getEmail(),
                    (String) p.get("name"), (String) p.get("picture"), p.getEmailVerified());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Google token verification failed: " + e.getMessage());
        }
    }

    public record GoogleUserInfo(
            String googleId, String email, String name, String picture, Boolean emailVerified) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// APPLICATION — exceptions
// ─────────────────────────────────────────────────────────────────────────────
