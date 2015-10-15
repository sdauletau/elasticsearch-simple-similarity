/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.index.similarity;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleSimilarity extends Similarity {
    protected final Settings settings;

    @Inject
    public SimpleSimilarity(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String toString() {
        return "SimpleSimilarity";
    }

    @Override
    public long computeNorm(FieldInvertState fieldInvertState) {
        // ignore field boost and length during indexing
        return 1;
    }

    @Override
    public final SimWeight computeWeight(float queryBoost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        final Explanation idf = termStats.length == 1
                ? idfExplain(collectionStats, termStats[0])
                : idfExplain(collectionStats, termStats);
        return new IDFStats(idf, queryBoost);
    }

    @Override
    public final SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
        IDFStats idfstats = (IDFStats) stats;
        return new SimpleTFIDFSimScorer(idfstats);
    }

    public float sloppyFreq(int distance) {
        return 1.0f / (distance + 1);
    }

    public float scorePayload(int doc, int start, int end, BytesRef payload) {
        return 1.0f;
    }

    public float tf(float freq) {
        return 1.0f;
    }

    public float idf(long docFreq, long numDocs) {
        return 1.0f;
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
        final long df = termStats.docFreq();
        final long max = collectionStats.maxDoc();
        final float idf = idf(df, max);
        return Explanation.match(idf, "idf(docFreq=" + df + ", maxDocs=" + max + ")");
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
        final long max = collectionStats.maxDoc();
        float idf = 0.0f;
        List<Explanation> subs = new ArrayList<>();
        for (final TermStatistics stat : termStats) {
            final long df = stat.docFreq();
            final float termIdf = idf(df, max);
            subs.add(Explanation.match(termIdf, "idf(docFreq=" + df + ", maxDocs=" + max + ")"));
            idf += termIdf;
        }
        return Explanation.match(idf, "idf(), sum of:", subs);
    }

    private final class SimpleTFIDFSimScorer extends SimScorer {
        private final IDFStats stats;
        private final float weightValue;

        SimpleTFIDFSimScorer(IDFStats stats) throws IOException {
            this.stats = stats;
            this.weightValue = stats.value;
        }

        @Override
        public float score(int doc, float freq) {
            return tf(freq) * weightValue;
        }

        @Override
        public float computeSlopFactor(int distance) {
            return sloppyFreq(distance);
        }

        @Override
        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
            return scorePayload(doc, start, end, payload);
        }

        @Override
        public Explanation explain(int doc, Explanation freq) {
            return explainScore(doc, freq, stats);
        }
    }

    private static class IDFStats extends SimWeight {
        private final Explanation idf;
        private final float queryBoost;
        private float value;

        public IDFStats(Explanation idf, float queryBoost) {
            this.idf = idf;
            this.queryBoost = queryBoost;
        }

        /** The value for normalization of contained query clauses (e.g. sum of squared weights).
         * <p>
         * NOTE: a Similarity implementation might not use any query normalization at all,
         * it's not required. However, if it wants to participate in query normalization,
         * it can return a value here.
         */
        @Override
        public float getValueForNormalization() {
            Loggers.getLogger(getClass()).debug("\n getValueForNormalization called \n");

            return queryBoost * queryBoost;
        }

        /** Assigns the query normalization factor and boost from parent queries to this.
         * <p>
         * NOTE: a Similarity implementation might not use this normalized value at all,
         * it's not required. However, it's usually a good idea to at least incorporate
         * the topLevelBoost (e.g. from an outer BooleanQuery) into its score.
         */
        @Override
        public void normalize(float queryNorm, float topLevelBoost) {
            Loggers.getLogger(getClass()).debug("\n normalize called \n");

            value = queryBoost * topLevelBoost * idf.getValue();
        }
    }

    private Explanation explainQuery(IDFStats stats) {
        List<Explanation> explanations = new ArrayList<>();

        Explanation boostExpl = Explanation.match(stats.queryBoost, "boost");
        if (stats.queryBoost != 1.0f)
            explanations.add(boostExpl);
        explanations.add(stats.idf);

        return Explanation.match(
                boostExpl.getValue() * stats.idf.getValue(),
                "queryWeight, product of:", explanations);
    }

    private Explanation explainField(int doc, Explanation freq, IDFStats stats) {
        Explanation tfExplanation = Explanation.match(tf(freq.getValue()), "tf(freq="+freq.getValue()+"), with freq of:", freq);

        return Explanation.match(
                tfExplanation.getValue() * stats.idf.getValue(),
                "fieldWeight in " + doc + ", product of:",
                tfExplanation, stats.idf);
    }

    private Explanation explainScore(int doc, Explanation freq, IDFStats stats) {
        Explanation queryExpl = explainQuery(stats);
        Explanation fieldExpl = explainField(doc, freq, stats);
        return Explanation.match(
                queryExpl.getValue() * fieldExpl.getValue(),
                "score(doc="+doc+", freq="+freq.getValue()+"), product of:",
                queryExpl, fieldExpl);
    }
}
