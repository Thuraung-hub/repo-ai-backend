package th.ac.mfu.repoai.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties.Http;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import th.ac.mfu.repoai.domain.User;
import th.ac.mfu.repoai.repository.UserRespsitory;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserRespsitory userRespsitory;

    @GetMapping("/")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRespsitory.findAll();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }
    
    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        return new ResponseEntity<>("Hello !!!", HttpStatus.OK);
    }
}
