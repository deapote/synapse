package com.synapse.kb.port.out;

import java.io.InputStream;

/** 文档解析出站端口。 */
public interface DocumentParserPort {

    String parse(InputStream inputStream, String fileName);
}
