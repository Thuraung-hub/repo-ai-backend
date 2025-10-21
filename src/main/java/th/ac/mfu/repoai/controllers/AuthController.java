package th.ac.mfu.repoai.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
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
import th.ac.mfu.repoai.services.GitServices;

@Controller
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private GitServices gitServices;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/token")
    @ResponseBody
    public ResponseEntity<String> token(Authentication principal) {
        ResponseEntity<OAuth2AccessToken> response = gitServices.loadGitHubToken(principal);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Not authenticated");
        }

        return ResponseEntity.ok(response.getBody().getTokenValue());
    }

    @GetMapping("/login")
    public ResponseEntity<User> login(Authentication principal) {
        // Load the token for this user
        gitServices.loadGitHubToken(principal);

        // Get user info from GitHub (includes email handling)
        ResponseEntity<Map<String, Object>> userInfoResponse = gitServices.getUserInfo();

        if (!userInfoResponse.getStatusCode().is2xxSuccessful() || userInfoResponse.getBody() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> attributes = userInfoResponse.getBody();

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
