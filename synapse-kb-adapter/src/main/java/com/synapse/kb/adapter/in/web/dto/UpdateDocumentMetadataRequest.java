package com.synapse.kb.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.time.LocalDate;
import java.util.Optional;

public record UpdateDocumentMetadataRequest(
        @JsonSetter(nulls = Nulls.SET) Optional<String> sourceType,
        @JsonSetter(nulls = Nulls.SET) Optional<String> canonicalKey,
        @JsonSetter(nulls = Nulls.SET) Optional<String> versionLabel,
        @JsonSetter(nulls = Nulls.SET) Optional<LocalDate> effectiveFrom,
        @JsonSetter(nulls = Nulls.SET) Optional<LocalDate> effectiveTo,
        @JsonSetter(nulls = Nulls.SET) Optional<Integer> authorityLevel,
        @JsonSetter(nulls = Nulls.SET) Optional<String> jurisdiction
) {
    public UpdateDocumentMetadataRequest() {
        this(Optional.empty(), Optional.empty(), Optional.empty(),
             Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
