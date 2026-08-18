package com.example.jpetstore.backend.domain.account;

/**
 * ログイン/再水和（{@code /api/auth/login}・{@code /api/auth/me}）で返す自分自身の設定値の read-model（#36/#25）。
 *
 * <p>{@code m_profile} の {@code color_scheme_preference}/{@code language_preference}
 * 単体の軽量SELECT用（{@link AccountEditDetail} のような {@code m_account}⋈{@code m_profile}
 * JOINやversionは持たない）。{@link #colorSchemePreference} は 'system'/'light'/'dark'
 * のいずれか（DB値そのまま・pass-through）、{@link #languagePreference} は 'english'/'japanese'
 * のいずれか（DB値そのまま。frontendの'en'/'ja'への変換はAPI境界=frontend側で行う）。
 */
public record UserPreferences(String colorSchemePreference, String languagePreference) {}
