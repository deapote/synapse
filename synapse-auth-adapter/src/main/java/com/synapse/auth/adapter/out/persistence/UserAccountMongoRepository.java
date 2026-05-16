package com.synapse.auth.adapter.out.persistence;

import com.synapse.auth.adapter.out.persistence.entity.UserAccountDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountMongoRepository extends MongoRepository<UserAccountDocument, String> {
    Optional<UserAccountDocument> findByUsername(String username);

    boolean existsByUsername(String username);

    List<UserAccountDocument> findAllBy(Pageable pageable);
}
