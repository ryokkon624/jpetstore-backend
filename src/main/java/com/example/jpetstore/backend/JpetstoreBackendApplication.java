package com.example.jpetstore.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
  "com.example.jpetstore.backend.infrastructure.mybatis.generated.mapper",
  // AC7: t_audit_log 用の手書き Mapper（MyBatis Generator 生成対象外）
  "com.example.jpetstore.backend.infrastructure.audit"
})
public class JpetstoreBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(JpetstoreBackendApplication.class, args);
  }
}
