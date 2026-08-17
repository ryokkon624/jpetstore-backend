package com.example.jpetstore.backend.infrastructure.mybatis.account;

import com.example.jpetstore.backend.domain.account.AccountContact;
import com.example.jpetstore.backend.domain.account.AccountRepository;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountContactCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountContactCustomMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link AccountRepository} の MyBatis実装（#30・{@code backend-conventions} §9・#29 Cart PoCテンプレ踏襲）。
 *
 * <p>{@link AccountContactCustomMapper} を保持し、戻り値は常にDomainモデル（{@link AccountContact}）のみ（{@code
 * *CustomEntity} をApplication層へ出さない）。
 */
@Repository
public class MyBatisAccountRepository implements AccountRepository {

  private final AccountContactCustomMapper accountContactCustomMapper;

  public MyBatisAccountRepository(AccountContactCustomMapper accountContactCustomMapper) {
    this.accountContactCustomMapper = accountContactCustomMapper;
  }

  @Override
  public Optional<AccountContact> findContactByUserId(Long userId) {
    AccountContactCustomEntity entity = accountContactCustomMapper.findByUserId(userId);
    return Optional.ofNullable(entity).map(this::toAccountContact);
  }

  private AccountContact toAccountContact(AccountContactCustomEntity entity) {
    return new AccountContact(
        entity.getFirstName(),
        entity.getLastName(),
        entity.getEmail(),
        entity.getPhone(),
        entity.getAddress1(),
        entity.getAddress2(),
        entity.getCity(),
        entity.getState(),
        entity.getPostalCode(),
        entity.getCountry());
  }
}
