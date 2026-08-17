package com.example.jpetstore.backend.infrastructure.mybatis.account;

import com.example.jpetstore.backend.domain.account.AccountContact;
import com.example.jpetstore.backend.domain.account.AccountRepository;
import com.example.jpetstore.backend.domain.account.NewAccountRegistration;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountContactCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountRegistrationCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProfileRegistrationCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.SignonRegistrationCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountContactCustomMapper;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountRegistrationCustomMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link AccountRepository} の MyBatis実装（#30・{@code backend-conventions} §9・#29 Cart PoCテンプレ踏襲）。
 *
 * <p>{@link AccountContactCustomMapper}/{@link AccountRegistrationCustomMapper}
 * を保持し、戻り値は常にDomainモデル （{@link AccountContact}・生成userId）のみ（{@code *CustomEntity}
 * をApplication層へ出さない）。
 *
 * <p>#13: {@link #register} の {@code create_user_id}/{@code update_user_id} は未認証guestによる登録のため
 * 常にNULL（E7）。{@code MyBatisCartRepository}/{@code MyBatisOrderRepository} と異なり {@code
 * CurrentUserProvider} には依存しない（登録時点では認証プリンシパルが存在しない）。
 */
@Repository
public class MyBatisAccountRepository implements AccountRepository {

  private static final String STATUS_OK = "OK";

  private final AccountContactCustomMapper accountContactCustomMapper;
  private final AccountRegistrationCustomMapper accountRegistrationCustomMapper;

  public MyBatisAccountRepository(
      AccountContactCustomMapper accountContactCustomMapper,
      AccountRegistrationCustomMapper accountRegistrationCustomMapper) {
    this.accountContactCustomMapper = accountContactCustomMapper;
    this.accountRegistrationCustomMapper = accountRegistrationCustomMapper;
  }

  @Override
  public Optional<AccountContact> findContactByUserId(Long userId) {
    AccountContactCustomEntity entity = accountContactCustomMapper.findByUserId(userId);
    return Optional.ofNullable(entity).map(this::toAccountContact);
  }

  @Override
  public Long register(NewAccountRegistration registration) {
    AccountRegistrationCustomEntity account = toAccountEntity(registration);
    accountRegistrationCustomMapper.insertAccount(account);
    Long userId = account.getUserId();

    accountRegistrationCustomMapper.insertSignon(toSignonEntity(userId, registration));
    accountRegistrationCustomMapper.insertProfile(toProfileEntity(userId, registration));
    return userId;
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

  private AccountRegistrationCustomEntity toAccountEntity(NewAccountRegistration registration) {
    AccountRegistrationCustomEntity entity = new AccountRegistrationCustomEntity();
    entity.setUsername(registration.username());
    entity.setEmail(registration.email());
    entity.setFirstName(registration.firstName());
    entity.setLastName(registration.lastName());
    entity.setStatus(STATUS_OK);
    entity.setAddress1(registration.address1());
    entity.setAddress2(registration.address2());
    entity.setCity(registration.city());
    entity.setState(registration.state());
    entity.setPostalCode(registration.postalCode());
    entity.setCountry(registration.country());
    entity.setPhone(registration.phone());
    entity.setCreateUserId(null);
    entity.setUpdateUserId(null);
    return entity;
  }

  private SignonRegistrationCustomEntity toSignonEntity(
      Long userId, NewAccountRegistration registration) {
    SignonRegistrationCustomEntity entity = new SignonRegistrationCustomEntity();
    entity.setUserId(userId);
    entity.setPasswordHash(registration.passwordHash());
    entity.setCreateUserId(null);
    entity.setUpdateUserId(null);
    return entity;
  }

  private ProfileRegistrationCustomEntity toProfileEntity(
      Long userId, NewAccountRegistration registration) {
    ProfileRegistrationCustomEntity entity = new ProfileRegistrationCustomEntity();
    entity.setUserId(userId);
    entity.setLanguagePreference(registration.languagePreference());
    entity.setFavoriteCategoryId(registration.favoriteCategoryId());
    entity.setCreateUserId(null);
    entity.setUpdateUserId(null);
    return entity;
  }
}
