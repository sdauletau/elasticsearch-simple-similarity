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
            return new SimpleSimilarityWeight(boost, collectionStats, termStats[0]);
        } else {
            return new SimpleSimilarityWeight(boost, collectionStats, termStats);
        }
    }

    private static class SimpleSimilarityWeight extends SimWeight {
        private final Explanation score;
        private float boost;

        SimpleSimilarityWeight(float boost, CollectionStatistics collectionStats, TermStatistics termStats) {
            this.score = Explanation.match(1.0f, "term score");
            this.boost = boost;
        }

        SimpleSimilarityWeight(float boost, CollectionStatistics collectionStats, TermStatistics termStats[]) {
            float total = 0.0f;
            List<Explanation> scores = new ArrayList<>();
            for (final TermStatistics stat : termStats) {
                scores.add(Explanation.match(1.0f, "term score"));
                total += 1.0f;
            }
            this.score = Explanation.match(total, "total terms score, sum of:", scores);
            this.boost = boost;
        }
    }

    public final SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
        SimpleSimilarityWeight simpleSimilarityWeight = (SimpleSimilarityWeight) weight;
        return new SimpleSimilarityScorer(simpleSimilarityWeight);
    }

    private final class SimpleSimilarityScorer extends SimScorer {
        private final SimpleSimilarityWeight weight;

        SimpleSimilarityScorer(SimpleSimilarityWeight weight) throws IOException {
            this.weight = weight;
        }

        public float score(int doc, float freq) {
            return weight.boost;
        }

        public float computeSlopFactor(int distance) {
            return 1.0f / (distance + 1);
        }

        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
            return 1.0f;
        }

        public Explanation explain(int doc, Explanation freq) {
            return explainScore(doc, freq, weight);
        }

        private Explanation explainQuery(SimpleSimilarityWeight weight) {
            Explanation boost = Explanation.match(weight.boost, "boost");
            return Explanation.match(boost.getValue(),"query score, result of:", boost);
        }

        private Explanation explainField(SimpleSimilarityWeight weight) {
            return Explanation.match(weight.score.getValue(),"field score, result of:", weight.score);
        }

        private Explanation explainScore(int doc, Explanation freq, SimpleSimilarityWeight weight) {
            Explanation queryExpl = explainQuery(weight);
            Explanation fieldExpl = explainField(weight);
            return Explanation.match(
                    queryExpl.getValue() * fieldExpl.getValue(),
                    "score, product of:",
                    queryExpl, fieldExpl);
        }
    }
}
