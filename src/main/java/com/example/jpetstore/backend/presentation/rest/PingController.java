package com.example.jpetstore.backend.presentation.rest;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 動作確認用の疎通エンドポイント。 */
@RestController
@RequestMapping("/api")
public class PingController {

  @GetMapping("/ping")
  public Map<String, String> ping() {
    return Map.of("status", "ok");
  }
}
