package th.ac.mfu.repoai.controllers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import th.ac.mfu.repoai.domain.User;
import th.ac.mfu.repoai.repository.UserRepository;

@Controller
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;
    
    @Autowired
    private UserRepository userRepository;
  

    @GetMapping("/token")
    @ResponseBody
    public ResponseEntity<String> token(@AuthenticationPrincipal OAuth2User principal) {
        String clientRegistrationId = "github";
        OAuth2AuthorizedClient client = authorizedClientService
                .loadAuthorizedClient(clientRegistrationId, principal.getName());

        if (client == null || client.getAccessToken() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Not authenticated");
        }

        OAuth2AccessToken token = client.getAccessToken();

        // Check expiry
        // if (token.getExpiresAt() != null &&
        // token.getExpiresAt().isBefore(Instant.now())) {
        // return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        // .body("Token expired");
        // }

        // ✅ Validate the token with GitHub API
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/user"))
                    .header("Authorization", "Bearer " + token.getTokenValue())
                    .GET()
                    .build();

            HttpClient clientHttp = HttpClient.newHttpClient();
            HttpResponse<String> response = clientHttp.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
            if (response.statusCode() != 200) {
                // Token revoked or invalid
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid GitHub token, please login again");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("GitHub validation failed: " + e.getMessage());
        }

        // If still valid
        return ResponseEntity.ok(token.getTokenValue());
    }

    @GetMapping("/login")
    public ResponseEntity<User> login(
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> attributes = principal.getAttributes();
        System.out.println(attributes);
        String githubId = String.valueOf(attributes.get("id"));
        String username = (String) attributes.get("login");
        String email = (String) attributes.get("email");
        String avatarUrl = (String) attributes.get("avatar_url");
        String profileUrl = (String) attributes.get("html_url");

        User user = userRepository.findByGithubId(githubId).orElseGet(() -> {
            User newUser = new User();
            newUser.setGithubId(githubId);
            newUser.setUsername(username);
            newUser.setEmail(email);
            newUser.setAvatarUrl(avatarUrl);
            newUser.setProfileUrl(profileUrl);
            return userRepository.save(newUser);
        });

        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        request.logout(); // invalidates session and clears OAuth
        return "redirect:/";
    }
}