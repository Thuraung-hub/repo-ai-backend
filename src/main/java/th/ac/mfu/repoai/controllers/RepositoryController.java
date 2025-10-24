package th.ac.mfu.repoai.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import th.ac.mfu.repoai.domain.User;
import th.ac.mfu.repoai.domain.Repository;
import th.ac.mfu.repoai.repository.RepositoryRepository;
import th.ac.mfu.repoai.repository.UserRepository;
import th.ac.mfu.repoai.services.GitServices;

@RestController
@RequestMapping("/api/repos")
public class RepositoryController {
    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitServices gitServices;

    public RepositoryController(UserRepository userRepository,
            RepositoryRepository repositoryRepository,
            GitServices gitServices) {
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
        this.gitServices = gitServices;
    }

    // List repositories saved for a user (by user's GitHub ID)
    @GetMapping("/")
    public ResponseEntity<?> getRepos(@RequestParam long githubId) {
        User user = userRepository.findByGithubId(githubId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Repository> repos = repositoryRepository.findByUser(user);
        return ResponseEntity.ok(repos);
    }

    // (Removed) DB-only create/update/delete endpoints have been replaced by GitHub-backed endpoints below.

    // =========================
    // GitHub-backed operations
    // =========================

    @PostMapping("/")
    public ResponseEntity<?> createRepos(@RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String description = (String) payload.get("description");
        Boolean isPrivate = (Boolean) payload.get("private");
        String defaultBranch = (String) payload.get("default_branch");
        Boolean autoInit = (Boolean) payload.get("auto_init");
        return gitServices.createUserRepositoryAndSave(name, description, isPrivate, defaultBranch, autoInit);
    }

    @PutMapping("/{owner}/{repo}")
    public ResponseEntity<?> updateRepos(@PathVariable String owner,
            @PathVariable String repo,
            @RequestBody Map<String, Object> updates) {
        // Guard: GitHub expects repo NAME here, not numeric repo_id
        if (repo != null && repo.matches("\\d+")) {
            return ResponseEntity.badRequest().body("Path parameter 'repo' must be the repository name, not the numeric id");
        }
        return gitServices.updateRepositoryAndSave(owner, repo, updates);
    }

    @DeleteMapping("/{owner}/{repo}")
    public ResponseEntity<?> deleteRepos(@PathVariable String owner, @PathVariable String repo) {
        // Guard: GitHub expects repo NAME here, not numeric repo_id
        if (repo != null && repo.matches("\\d+")) {
            return ResponseEntity.badRequest().body("Path parameter 'repo' must be the repository name, not the numeric id");
        }
        return gitServices.deleteRepositoryAndRemove(owner, repo);
    }
}
