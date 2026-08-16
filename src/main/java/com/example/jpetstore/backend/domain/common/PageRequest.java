package com.example.jpetstore.backend.domain.common;

/**
 * 一覧APIのページング要求を表す汎用VO（#1・ユーザー承認 2026-08-16・論点3）。
 *
 * <p>1-index（{@code page} は1始まり）。{@link #of(Integer, Integer, int, int)} でクライアント入力を正規化する（page &lt;
 * 1 は1に、size &lt; 1 は既定値に、size &gt; cap は cap に丸める＝クランプであり400エラーにはしない）。 {@link Page} と対で {@code
 * domain.common} に置く（#2/#9 が再利用する先例）。
 */
public record PageRequest(int page, int size) {

  public static final int DEFAULT_SIZE = 12;
  public static final int MAX_SIZE = 100;

  public PageRequest {
    if (page < 1) {
      page = 1;
    }
    if (size < 1) {
      size = DEFAULT_SIZE;
    }
    if (size > MAX_SIZE) {
      size = MAX_SIZE;
    }
  }

  /** クライアント指定の {@code page}/{@code size}（null可）を既定値・cap（{@link #MAX_SIZE}）で正規化する。 */
  public static PageRequest of(Integer page, Integer size) {
    return new PageRequest(page == null ? 1 : page, size == null ? DEFAULT_SIZE : size);
  }

  /** MyBatis の {@code LIMIT ... OFFSET ...} に渡すoffset（0-index換算）。 */
  public int offset() {
    return (page - 1) * size;
  }
}
