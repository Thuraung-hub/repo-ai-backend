package th.ac.mfu.repoai.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import th.ac.mfu.repoai.domain.User;

public interface UserRespsitory extends CrudRepository<User, Long> {
    List<User> findAll();
}