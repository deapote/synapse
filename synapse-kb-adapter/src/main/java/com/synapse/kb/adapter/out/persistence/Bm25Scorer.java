package com.synapse.kb.adapter.out.persistence;

import java.util.List;
import java.util.Map;

class Bm25Scorer {
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    double bm25(PostingCandidate document, List<String> queryTokens,
                Map<String, Long> documentFrequencies, long totalChunks, double avgTokenCount) {
        double score = 0;
        int documentLength = Math.max(1, document.tokenCount);
        for (String token : queryTokens) {
            int tf = document.termFrequencies.getOrDefault(token, 0);
            if (tf <= 0) {
                continue;
            }
            long df = documentFrequencies.getOrDefault(token, 1L);
            double idf = Math.log(1 + (totalChunks - df + 0.5) / (df + 0.5));
            double denominator = tf + K1 * (1 - B + B * documentLength / Math.max(1.0, avgTokenCount));
            score += idf * (tf * (K1 + 1)) / denominator;
        }
        return score;
    }

    float normalize(double score, double maxScore) {
        if (maxScore <= 0) {
            return 0;
        }
        return (float) Math.max(0, Math.min(1, score / maxScore));
    }
}
