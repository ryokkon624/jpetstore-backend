package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.account.AccountRepository;
import com.example.jpetstore.backend.domain.account.NewAccountRegistration;
import com.example.jpetstore.backend.domain.account.RegisterAccountCommand;
import com.example.jpetstore.backend.domain.exception.UsernameAlreadyExistsException;
import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.infrastructure.security.RegisterAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザー登録ユースケース（#13 AC1〜AC6・[L2]・AC-neg1/AC-neg2）。
 *
 * <p>①DB-backedレート制限ゲート（{@link RegisterAttemptService#assertNotRateLimited}・IPキー・AC4/SBD-6）
 * →②{@code password==repeatedPassword}検証（不一致は400）→③bcryptエンコード（AC3・SBD-5）→ ④{@link
 * AccountRepository#register}でm_account/m_signon/m_profileへ atomic に
 * INSERT（{@code @Transactional}）→⑤username重複（{@link DuplicateKeyException}）は{@link
 * UsernameAlreadyExistsException} に変換し409＋明示（E4）→⑥{@link
 * AuthApplicationService#issueTokensFor}（login末尾から抽出・E9）で 照合スキップのfresh JWT自動ログイン、の順に処理する。
 *
 * <p>{@link RegisterAttemptService#recordAttempt}は結果（成功/400/409）を問わず必ず1回呼ぶ（登録は「1回の
 * 試行」自体がIP由来の資源消費であり、{@code LoginAttemptService}の「失敗のみ記録」とは異なる設計。詳細は {@code
 * backlog/sprint_16/implementation-notes.md}）。ただしレート制限ゲート自体で短絡した場合は呼ばない （{@code
 * AuthApplicationService#login}が{@code assertNotLocked}短絡時に{@code recordFailure}を呼ばないのと 同じ設計）。
 *
 * <p>client_ipは{@code request.getRemoteAddr()}から解決する（X-Forwarded-Forは信頼しない・Sprint2教訓）。
 *
 * <p>{@code application.service}配下に置くことで{@code ProgramContextAspect}が本クラスのメソッドを機能識別子
 * としてWHOカラムへ自動付与する。
 */
@Service
public class RegistrationApplicationService {

  private static final String DEFAULT_LANGUAGE_PREFERENCE = "english";
  private static final List<String> DEFAULT_ROLES = List.of("USER");

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final RegisterAttemptService registerAttemptService;
  private final AuthApplicationService authApplicationService;

  public RegistrationApplicationService(
      AccountRepository accountRepository,
      PasswordEncoder passwordEncoder,
      RegisterAttemptService registerAttemptService,
      AuthApplicationService authApplicationService) {
    this.accountRepository = accountRepository;
    this.passwordEncoder = passwordEncoder;
    this.registerAttemptService = registerAttemptService;
    this.authApplicationService = authApplicationService;
  }

  /**
   * アカウントを登録し、成功すれば照合をスキップしたfresh JWTで自動ログインする。
   *
   * @throws com.example.jpetstore.backend.domain.exception.RegistrationRateLimitExceededException
   *     clientIpが直近窓内でレート制限中の場合（429）
   * @throws IllegalArgumentException {@code password}と{@code repeatedPassword}が一致しない場合（400）
   * @throws UsernameAlreadyExistsException {@code username}が既存行と重複する場合（409）
   */
  @Transactional
  public AuthenticatedUser register(
      RegisterAccountCommand command, HttpServletRequest request, HttpServletResponse response) {
    String clientIp = request.getRemoteAddr();
    registerAttemptService.assertNotRateLimited(clientIp);

    try {
      if (!command.password().equals(command.repeatedPassword())) {
        throw new IllegalArgumentException("Passwords do not match");
      }

      String passwordHash = passwordEncoder.encode(command.password());
      NewAccountRegistration registration = toRegistration(command, passwordHash);

      Long userId;
      try {
        userId = accountRepository.register(registration);
      } catch (DuplicateKeyException e) {
        throw new UsernameAlreadyExistsException();
      }

      AuthenticatedUser user = new AuthenticatedUser(userId, command.username(), DEFAULT_ROLES);
      authApplicationService.issueTokensFor(user, response);
      return user;
    } finally {
      registerAttemptService.recordAttempt(clientIp);
    }
  }

  private NewAccountRegistration toRegistration(
      RegisterAccountCommand command, String passwordHash) {
    String languagePreference =
        command.languagePreference() == null || command.languagePreference().isBlank()
            ? DEFAULT_LANGUAGE_PREFERENCE
            : command.languagePreference();
    return new NewAccountRegistration(
        command.username(),
        passwordHash,
        command.email(),
        command.firstName(),
        command.lastName(),
        command.address1(),
        command.address2(),
        command.city(),
        command.state(),
        command.postalCode(),
        command.country(),
        command.phone(),
        languagePreference,
        command.favoriteCategoryId());
  }
}
