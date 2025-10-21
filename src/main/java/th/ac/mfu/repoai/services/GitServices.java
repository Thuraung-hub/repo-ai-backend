package th.ac.mfu.repoai.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.util.List;
import java.util.Map;

@Service
public class GitServices {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private OAuth2AccessToken token; // Store token here

    public GitServices(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    /**
     * Load GitHub OAuth2 access token for the logged-in user
     */
    public ResponseEntity<OAuth2AccessToken> loadGitHubToken(Authentication principal) {
        String clientRegistrationId = "github";

        OAuth2AuthorizedClient client = authorizedClientService
                .loadAuthorizedClient(clientRegistrationId, principal.getName());

        if (client == null || client.getAccessToken() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(null);
        }

        this.token = client.getAccessToken(); // Save token for future use
        return ResponseEntity.ok(token);
    }

    /**
     * Generic method to call GitHub API using the stored token
     */
    public ResponseEntity<String> callGitHubApi(String url) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("GitHub token not loaded, please authenticate first");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token.getTokenValue());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid GitHub token, please login again");
            }

            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("GitHub API call failed: " + e.getMessage());
        }
    }

    /**
     * Example: get /user info
     */
    public ResponseEntity<Map<String, Object>> getUserInfo() {
        ResponseEntity<String> response = callGitHubApi("https://api.github.com/user");

        if (!response.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(response.getStatusCode())
                    .body(Map.of("error", response.getBody()));
        }

        // Convert JSON string to Map
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> userInfo = mapper.readValue(response.getBody(), Map.class);

            // If email is null, get from /user/emails
            if (userInfo.get("email") == null) {
                ResponseEntity<String> emailsResponse = callGitHubApi("https://api.github.com/user/emails");
                List<Map<String, Object>> emails = mapper.readValue(emailsResponse.getBody(), List.class);

                String primaryEmail = emails.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                        .map(e -> (String) e.get("email"))
                        .findFirst()
                        .orElse(null);

                userInfo.put("email", primaryEmail);
            }

            return ResponseEntity.ok(userInfo);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to parse GitHub response: " + e.getMessage()));
        }
    }
}
