package com.example.jpetstore.backend.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnumGenerator {

  private static final String JDBC_URL =
      "jdbc:mysql://localhost:3306/jpetstore_db?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Tokyo";
  private static final String JDBC_USER = "jpetstore";
  private static final String JDBC_PASSWORD = "jpetstore";

  // 出力先パス
  private static final Path OUTPUT_DIR =
      Path.of("src/main/java/com/example/jpetstore/backend/domain/enums");

  private static final String PACKAGE_NAME = "com.example.jpetstore.backend.domain.enums";

  public static void main(String[] args) {
    try {
      new EnumGenerator().generateAllEnums();
      System.out.println("Enum generation finished successfully.");
    } catch (Exception e) {
      System.err.println("Enum generation failed:");
      e.printStackTrace();
      System.exit(1);
    }
  }

  public void generateAllEnums() throws Exception {
    // 出力ディレクトリがなければ作成
    if (!Files.exists(OUTPUT_DIR)) {
      Files.createDirectories(OUTPUT_DIR);
    }

    try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
      Map<String, String> typeToClassName = loadCodeTypes(conn);
      for (Map.Entry<String, String> entry : typeToClassName.entrySet()) {
        String codeType = entry.getKey();
        String className = entry.getValue();
        List<CodeRow> rows = loadCodesByType(conn, codeType);

        if (rows.isEmpty()) {
          continue;
        }

        String source = generateEnumSource(className, rows);
        writeJavaFile(className, source);
        System.out.println("Generated enum: " + className + " (" + rows.size() + " values)");
      }
    }
  }

  /** code_type ごとの code_type_name_en を取得 */
  private Map<String, String> loadCodeTypes(Connection conn) throws SQLException {
    String sql =
        """
        SELECT DISTINCT code_type, code_type_name_en
        FROM m_code
        ORDER BY code_type
        """;

    Map<String, String> result = new LinkedHashMap<>();
    try (PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {

      while (rs.next()) {
        String codeType = rs.getString("code_type");
        String className = rs.getString("code_type_name_en");
        result.put(codeType, className);
      }
    }
    return result;
  }

  /** code_type の全コードを display_order → code_value で取得 */
  private List<CodeRow> loadCodesByType(Connection conn, String codeType) throws SQLException {
    String sql =
        """
        SELECT code_value, display_name_en
        FROM m_code
        WHERE code_type = ?
        ORDER BY display_order, code_value
        """;

    List<CodeRow> rows = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, codeType);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String codeValue = rs.getString("code_value");
          String displayNameEn = rs.getString("display_name_en");
          rows.add(new CodeRow(codeValue, displayNameEn));
        }
      }
    }
    return rows;
  }

  /** 1つの enum クラスのソースコードを生成 */
  private String generateEnumSource(String className, List<CodeRow> rows) {
    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(PACKAGE_NAME).append(";\n\n");
    sb.append("public enum ").append(className).append(" implements CodeEnum {\n");

    // enum 定数
    for (int i = 0; i < rows.size(); i++) {
      CodeRow row = rows.get(i);
      String constantName = toEnumConstantName(row.displayNameEn());
      sb.append("    ").append(constantName).append("(\"").append(row.codeValue()).append("\")");
      if (i < rows.size() - 1) {
        sb.append(",");
      } else {
        sb.append(";");
      }
      sb.append("\n");
    }
    sb.append("\n");

    // フィールド & コンストラクタ & getter
    sb.append("    private final String code;\n\n");
    sb.append("    ").append(className).append("(String code) {\n");
    sb.append("        this.code = code;\n");
    sb.append("    }\n\n");
    sb.append("    @Override\n");
    sb.append("    public String getCode() {\n");
    sb.append("        return code;\n");
    sb.append("    }\n\n");

    // fromCode
    sb.append("    public static ").append(className).append(" fromCode(String code) {\n");
    sb.append("        for (").append(className).append(" v : values()) {\n");
    sb.append("            if (v.code.equals(code)) {\n");
    sb.append("                return v;\n");
    sb.append("            }\n");
    sb.append("        }\n");
    sb.append("        throw new IllegalArgumentException(\"Invalid ")
        .append(className)
        .append(" code: \" + code);\n");
    sb.append("    }\n");

    sb.append("}\n");
    return sb.toString();
  }

  /** display_name_en → ENUM_CONST_NAME 変換 例: "Nth Weekday" → "NTH_WEEKDAY" */
  private String toEnumConstantName(String displayNameEn) {
    if (displayNameEn == null || displayNameEn.isBlank()) {
      return "UNKNOWN";
    }
    // 大文字化 & 非英数字をアンダースコアに
    String upper = displayNameEn.toUpperCase();
    StringBuilder sb = new StringBuilder();
    boolean prevUnderscore = false;
    for (int i = 0; i < upper.length(); i++) {
      char c = upper.charAt(i);
      if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        sb.append(c);
        prevUnderscore = false;
      } else {
        if (!prevUnderscore) {
          sb.append('_');
          prevUnderscore = true;
        }
      }
    }
    String result = sb.toString();
    // 先頭と末尾のアンダースコアを削る
    result = result.replaceAll("^_+", "").replaceAll("_+$", "");
    if (result.isEmpty()) {
      result = "VALUE";
    }
    // 先頭が数字だったらプレフィックスを付ける
    if (result.charAt(0) >= '0' && result.charAt(0) <= '9') {
      result = "_" + result;
    }
    return result;
  }

  private void writeJavaFile(String className, String source) throws IOException {
    Path file = OUTPUT_DIR.resolve(className + ".java");
    Files.writeString(
        file,
        source,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
  }

  private record CodeRow(String codeValue, String displayNameEn) {}
}
