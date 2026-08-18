package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.SignonUpdateCustomEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * userId起点で{@code m_signon}へアクセスするMapper（#15）。
 *
 * <p>既存の{@link AccountAuthCustomMapper}はusername起点・login専用のためPW変更（userId起点・{@code
 * CurrentUserProvider}で解決済み）には使えない。単純な単一PK SELECT/UPDATEのため、アノテーション方式 （{@code backend-conventions}
 * §9）を採用する。
 */
@Mapper
public interface SignonCustomMapper {

  /** 指定userIdの現在パスワードハッシュを取得する（現在PW再認証・AC1）。該当行が無ければ{@code null}。 */
  @Select("SELECT password_hash FROM m_signon WHERE user_id = #{userId}")
  String findPasswordHashByUserId(@Param("userId") Long userId);

  /**
   * パスワードハッシュを更新する（AC2）。{@code m_signon}はversion楽観ロック対象外のため{@code WHERE user_id}のみで ガード無し（{@code
   * version = version + 1}は整合のため加算のみ・計画フェーズ確定）。
   *
   * @return 影響行数（通常1。0は対象userId不存在＝認証済みリクエストでは通常起こり得ない）
   */
  @Update(
      """
      UPDATE m_signon
         SET password_hash = #{passwordHash}, version = version + 1,
             update_user_id = #{updateUserId}, update_program = #{updateProgram}
       WHERE user_id = #{userId}
      """)
  int updatePassword(SignonUpdateCustomEntity entity);
}
