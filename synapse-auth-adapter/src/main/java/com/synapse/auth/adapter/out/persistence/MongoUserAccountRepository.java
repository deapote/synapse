package com.synapse.auth.adapter.out.persistence;

import com.synapse.auth.adapter.out.persistence.entity.UserAccountDocument;
import com.synapse.auth.model.RoleName;
import com.synapse.auth.model.UserAccount;
import com.synapse.auth.model.UserId;
import com.synapse.auth.repository.UserAccountRepository;
import com.synapse.shared.exception.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户账号仓储适配器。将领域 {@link UserAccount} 与 MongoDB {@link UserAccountDocument} 映射，
 * 处理 username 唯一索引冲突的异常转换。
 */
@Component
public class MongoUserAccountRepository implements UserAccountRepository {
    private final UserAccountMongoRepository mongoRepository;

    public MongoUserAccountRepository(UserAccountMongoRepository mongoRepository) {
        this.mongoRepository = mongoRepository;
    }

    @Override
    public UserAccount save(UserAccount user) {
        try {
            return toEntity(mongoRepository.save(toDocument(user)));
        } catch (DuplicateKeyException e) {
            throw new DomainException("用户名已存在", e);
        }
    }

    @Override
    public Optional<UserAccount> findById(UserId id) {
        return mongoRepository.findById(id.value()).map(this::toEntity);
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return mongoRepository.findByUsername(username).map(this::toEntity);
    }

    @Override
    public List<UserAccount> findAll(int page, int size) {
        return mongoRepository.findAllBy(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().map(this::toEntity).toList();
    }

    @Override
    public boolean existsByUsername(String username) {
        return mongoRepository.existsByUsername(username);
    }

    private UserAccountDocument toDocument(UserAccount user) {
        UserAccountDocument doc = new UserAccountDocument();
        doc.setId(user.getId().value());
        doc.setUsername(user.getUsername());
        doc.setDisplayName(user.getDisplayName());
        doc.setPasswordHash(user.getPasswordHash());
        doc.setRoles(user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()));
        doc.setEnabled(user.isEnabled());
        doc.setCreatedAt(user.getCreatedAt());
        return doc;
    }

    private UserAccount toEntity(UserAccountDocument doc) {
        Set<RoleName> roles = doc.getRoles() == null || doc.getRoles().isEmpty()
                ? EnumSet.noneOf(RoleName.class)
                : doc.getRoles().stream().map(RoleName::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(RoleName.class)));
        return UserAccount.reconstruct(new UserId(doc.getId()), doc.getUsername(), doc.getDisplayName(),
                doc.getPasswordHash(), roles, doc.isEnabled(), doc.getCreatedAt());
    }
}
