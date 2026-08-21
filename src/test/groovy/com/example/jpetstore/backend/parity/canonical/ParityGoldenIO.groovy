package com.example.jpetstore.backend.parity.canonical

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

/**
 * golden JSON の読み書き（#48 AC8）。capture側（書き込み）・verify側（読み込み・classpath上のコミット済みgolden）の
 * 両方から共有する。
 */
class ParityGoldenIO {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /** {@code src/test/resources/parity/golden/<scenario>.json} をclasspath経由で読む（parityTest用・legacy不要）。 */
    static ParityGolden readFromClasspath(String scenario) {
        String path = "/parity/golden/${scenario}.json"
        InputStream stream = ParityGoldenIO.class.getResourceAsStream(path)
        if (stream == null) {
            throw new IllegalStateException(
                    "golden JSONが見つからない: ${path} (先に captureGolden で採取・コミットが必要)")
        }
        try {
            return MAPPER.readValue(stream, ParityGolden)
        } finally {
            stream.close()
        }
    }

    /** golden をファイルへ書き出す（captureGoldenタスク用）。メタ未設定ならAC8違反として書き出さずfailする。 */
    static void writeToFile(ParityGolden golden, File file) {
        golden.validateMetaOrThrow()
        file.parentFile?.mkdirs()
        MAPPER.writeValue(file, golden)
    }
}
