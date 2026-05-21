package com.synapse.kb.adapter.out.vector;

class MilvusFilterValueEscaper {
    String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
