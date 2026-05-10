package com.synapse.kb.port.out;

import java.io.InputStream;

/**
 * 文档解析端口（出站端口 / SPI）。
 *
 * <p>将二进制文档（PDF、Word 等）解析为纯文本。
 * 由适配器层（如 {@code ApacheTikaDocumentParserAdapter}）实现。
 */
public interface DocumentParserPort {

    /**
     * 解析文档为纯文本。
     *
     * @param inputStream 文档内容输入流
     * @param fileName    原始文件名，用于识别格式
     * @return 解析后的纯文本内容
     */
    String parse(InputStream inputStream, String fileName);
}
