package th.ac.mfu.repoai.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.util.HashMap;
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
                    .body("GitHub token not loaded, please login");
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
                    String.class);

            // ✅ Real check: GitHub will return 401 if token is invalid/revoked
            /// oauth2/authorization/github needs to be called again to re-authenticate
            if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                this.token = null; // clear token locally
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("GitHub token expired or revoked, please login again");
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

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> userInfo = mapper.readValue(response.getBody(), Map.class);

            // If email is null, fetch /user/emails
            if (userInfo.get("email") == null) {
                ResponseEntity<String> emailsResponse = callGitHubApi("https://api.github.com/user/emails");

                if (emailsResponse.getStatusCode().is2xxSuccessful()) {
                    List<Map<String, Object>> emails = mapper.readValue(emailsResponse.getBody(), List.class);
                    String primaryEmail = emails.stream()
                            .filter(e -> Boolean.TRUE.equals(e.get("primary"))
                                    && Boolean.TRUE.equals(e.get("verified")))
                            .map(e -> (String) e.get("email"))
                            .findFirst()
                            .orElse(null);
                    userInfo.put("email", primaryEmail);
                }
            }

            return ResponseEntity.ok(userInfo);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to parse GitHub response: " + e.getMessage()));
        }
    }

    public ResponseEntity<List<Map<String, Object>>> getUserRepositories() {
        ResponseEntity<String> response = callGitHubApi("https://api.github.com/user/repos");
        System.out.println("GitHub Repos Response: " + response.getBody());

        if (!response.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(response.getStatusCode())
                    .body(List.of(Map.of("error", response.getBody())));
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // Parse JSON array
            List<Map<String, Object>> repositories = mapper.readValue(
                    response.getBody(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            // Build a safe response list
            List<Map<String, Object>> simplifiedRepos = repositories.stream()
                    .map(repo -> {
                        Map<String, Object> safeMap = new HashMap<>();

                        safeMap.put("name", repo.get("name"));
                        safeMap.put("repo_id", repo.get("id"));
                        safeMap.put("full_name", repo.get("full_name"));
                        safeMap.put("html_url", repo.get("html_url"));
                        safeMap.put("description", repo.get("description"));
                        safeMap.put("default_branch", repo.get("default_branch"));
                        safeMap.put("private", repo.get("private"));

                        // Handle nested owner object safely
                        Map<String, Object> owner = (Map<String, Object>) repo.get("owner");
                        safeMap.put("owner", owner != null ? owner.get("login") : null);
                        safeMap.put("owner_github_id", owner != null ? owner.get("id") : null);

                        return safeMap;
                    })
                    .toList();

            return ResponseEntity.ok(simplifiedRepos);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of(Map.of("error", "Failed to parse repositories: " + e.getMessage())));
        }
    }

}
