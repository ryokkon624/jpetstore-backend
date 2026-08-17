/**
 * REST API の Presentation 層（{@code @RestController}）。
 *
 * <p>外部への露出面はこのパッケージ（HTTP/JSON REST・{@code SecurityConfig} により既定で認証必須）に限定する。 remoting/WS
 * 系（Hessian/Burlap/HttpInvoker/RMI/Axis/JAX-WS 等）のエクスポータ・エンドポイントは構造的に持たず、 新規に追加もしない（#11
 * AC1/AC-neg1・SBD-7）。この不在は {@code RemotingSurfaceAbsenceSpec}（同パッケージのテスト）で 回帰テスト固定している。
 *
 * <p>認可は Web 層（Controller）ではなく Service/Domain 層（{@code
 * com.example.jpetstore.backend.domain.security.OwnershipAuthorizationService}・{@code
 * CurrentUserProvider}）で 呼び出しチャネル非依存に強制する（#11 AC2/SBD-1）。Controller は Application Service
 * への委譲のみを行い、 認可分岐そのものは持たない。
 */
package com.example.jpetstore.backend.presentation.rest;
