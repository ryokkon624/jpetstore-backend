package com.example.jpetstore.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
  "com.example.jpetstore.backend.infrastructure.mybatis.generated.mapper",
  "com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper"
})
public class JpetstoreBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(JpetstoreBackendApplication.class, args);
  }
}
