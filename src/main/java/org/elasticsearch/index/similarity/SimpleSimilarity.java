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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleSimilarity extends Similarity {

    public SimpleSimilarity() {
    }

    public long computeNorm(FieldInvertState fieldInvertState) {
        // ignore field boost and length during indexing
        return 1;
    }

    public final SimWeight computeWeight(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        if (termStats.length == 1) {
            return new SimpleScore(boost, collectionStats, termStats[0]);
        } else {
            return new SimpleScore(boost, collectionStats, termStats);
        }
    }

    private static class SimpleScore extends SimWeight {
        private final Explanation score;
        private final Explanation boost;

        SimpleScore(float boost, CollectionStatistics collectionStats, TermStatistics termStats) {
            String description = String.format("simple score for (%s:%s)", collectionStats.field(), termStats.term().utf8ToString());
            this.score = Explanation.match(1.0f, description);
            this.boost = Explanation.match(boost, "boost");
        }

        SimpleScore(float boost, CollectionStatistics collectionStats, TermStatistics termStats[]) {
            float total = 0.0f;
            List<Explanation> scores = new ArrayList<>();
            for (final TermStatistics stat : termStats) {
                String description = String.format("simple score for (%s:%s)", collectionStats.field(), stat.term().utf8ToString());
                scores.add(Explanation.match(1.0f, description));
                total += 1.0f;
            }
            this.score = Explanation.match(total, "total score, sum of:", scores);
            this.boost = Explanation.match(boost, "boost");
        }
    }

    public final SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
        SimpleScore score = (SimpleScore) weight;
        return new SimpleScorer(score);
    }

    private final class SimpleScorer extends SimScorer {
        private final SimpleScore score;

        SimpleScorer(SimpleScore score) throws IOException {
            this.score = score;
        }

        public float score(int doc, float freq) {
            return score.score.getValue() * score.boost.getValue();
        }

        public float computeSlopFactor(int distance) {
            return 1.0f / (distance + 1);
        }

        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
            return 1.0f;
        }

        public Explanation explain(int doc, Explanation freq) {
            return explainScore(doc, freq, score);
        }

        private Explanation explainQuery(SimpleScore score) {
            return Explanation.match(score.boost.getValue(),"query score, result of:", score.boost);
        }

        private Explanation explainField(SimpleScore score) {
            return Explanation.match(score.score.getValue(),"field score, result of:", score.score);
        }

        private Explanation explainScore(int doc, Explanation freq, SimpleScore weight) {
            Explanation queryExpl = explainQuery(weight);
            Explanation fieldExpl = explainField(weight);
            return Explanation.match(queryExpl.getValue() * fieldExpl.getValue(),"score, product of:", queryExpl, fieldExpl);
        }
    }
}
