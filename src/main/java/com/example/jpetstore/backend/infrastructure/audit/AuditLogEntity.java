package com.example.jpetstore.backend.infrastructure.audit;

import java.time.LocalDateTime;

/**
 * {@code t_audit_log} の Infrastructure エンティティ（手書き。AC7）。
 *
 * <p>MyBatis Generator の生成物ではないが、既存の生成エンティティと同じ規約（Infrastructure 層に閉じる・ WHO カラムを持つ）に揃えている。{@code
 * createProgram}/{@code updateProgram} は {@link AuditProgramInterceptor} が未設定時に自動補完する（本テーブルへの書き込みは
 * {@code ..application.service..} を経由しないことが多いため、通常は "SYSTEM" が補完される想定）。
 */
public class AuditLogEntity {

  private Long auditId;
  private String eventType;
  private Long actorUserId;
  private String actorUsername;
  private String action;
  private String targetType;
  private String targetId;
  private String result;
  private String detail;
  private String clientIp;
  private Long createUserId;
  private String createProgram;
  private LocalDateTime createdAt;
  private Long updateUserId;
  private String updateProgram;
  private LocalDateTime updatedAt;

  public Long getAuditId() {
    return auditId;
  }

  public void setAuditId(Long auditId) {
    this.auditId = auditId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public Long getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(Long actorUserId) {
    this.actorUserId = actorUserId;
  }

  public String getActorUsername() {
    return actorUsername;
  }

  public void setActorUsername(String actorUsername) {
    this.actorUsername = actorUsername;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getClientIp() {
    return clientIp;
  }

  public void setClientIp(String clientIp) {
    this.clientIp = clientIp;
  }

  public Long getCreateUserId() {
    return createUserId;
  }

  public void setCreateUserId(Long createUserId) {
    this.createUserId = createUserId;
  }

  public String getCreateProgram() {
    return createProgram;
  }

  public void setCreateProgram(String createProgram) {
    this.createProgram = createProgram;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public Long getUpdateUserId() {
    return updateUserId;
  }

  public void setUpdateUserId(Long updateUserId) {
    this.updateUserId = updateUserId;
  }

  public String getUpdateProgram() {
    return updateProgram;
  }

  public void setUpdateProgram(String updateProgram) {
    this.updateProgram = updateProgram;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
