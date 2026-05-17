package com.synapse.kb.port.out;

/** Query 改写出站端口。 */
public interface QueryRewritePort {

    String rewrite(String query);
}
