package com.antixray.plugin.ml;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

/**
 * Implementação própria de Random Forest sem dependências externas.
 * <p>
 * Substitui o Smile ML (148MB) por uma implementação leve (~200KB) dos algoritmos
 * necessários: Random Forest com árvores de decisão CART, Gini impurity,
 * bootstrap aggregating (bagging) e seleção aleatória de features.
 * <p>
 * Uso:
 * {@code
 *   SimpleRandomForest rf = new SimpleRandomForest(100, 20, 5, seed);
 *   rf.fit(data, labels);
 *   int pred = rf.predict(features);
 *   double[] probs = new double[2];
 *   rf.predict(features, probs);
 * }
 */
public class SimpleRandomForest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private DecisionTree[] trees;
    private int numTrees;
    private int numFeatures;
    private int numClasses;
    private int maxDepth;
    private int minSamplesSplit;
    private int maxFeaturesPerSplit;
    private long seed;

    /**
     * Cria um Random Forest vazio. Deve chamar {@code fit()} antes de usar.
     */
    public SimpleRandomForest() {
        this.numTrees = 100;
        this.maxDepth = 20;
        this.minSamplesSplit = 5;
        this.seed = System.currentTimeMillis();
    }

    /**
     * Cria um Random Forest com os parâmetros especificados.
     *
     * @param numTrees        Número de árvores (default 100)
     * @param maxDepth        Profundidade máxima das árvores (default 20)
     * @param minSamplesSplit Mínimo de amostras para split (default 5)
     * @param seed            Semente aleatória (null → System.currentTimeMillis())
     */
    public SimpleRandomForest(int numTrees, int maxDepth, int minSamplesSplit, Long seed) {
        this.numTrees = (numTrees > 0) ? numTrees : 100;
        this.maxDepth = (maxDepth > 0) ? maxDepth : 20;
        this.minSamplesSplit = (minSamplesSplit > 0) ? minSamplesSplit : 5;
        this.seed = (seed != null) ? seed : System.currentTimeMillis();
    }

    /**
     * Treina o Random Forest com os dados fornecidos.
     *
     * @param data   Matriz de features [n_amostras][n_features]
     * @param labels Array de labels (0 ou 1)
     */
    public void fit(double[][] data, int[] labels) {
        if (data == null || data.length == 0 || labels == null || data.length != labels.length) {
            throw new IllegalArgumentException("Dados inválidos para treinamento");
        }

        int n = data.length;
        this.numFeatures = data[0].length;
        this.numClasses = 2; // LEGIT=0, CHEATER=1

        // Número de features para considerar em cada split: sqrt(numFeatures)
        this.maxFeaturesPerSplit = Math.max(1, (int) Math.sqrt(numFeatures));

        // Inicializa array de árvores
        this.trees = new DecisionTree[numTrees];

        // Treina cada árvore com bootstrap sampling
        for (int t = 0; t < numTrees; t++) {
            // Gera bootstrap sample (com reposição)
            double[][] bootstrapData = new double[n][];
            int[] bootstrapLabels = new int[n];
            Random rng = new Random(seed + t * 1000);

            for (int i = 0; i < n; i++) {
                int idx = rng.nextInt(n);
                bootstrapData[i] = data[idx];
                bootstrapLabels[i] = labels[idx];
            }

            // Treina árvore
            trees[t] = new DecisionTree();
            trees[t].fit(bootstrapData, bootstrapLabels, maxDepth,
                    minSamplesSplit, maxFeaturesPerSplit, new Random(seed + t * 1000 + 1));
        }
    }

    /**
     * Prediz a classe para um vetor de features.
     *
     * @param features Vetor de features
     * @return Classe predita (0 = LEGIT, 1 = CHEATER)
     */
    public int predict(double[] features) {
        if (trees == null || trees.length == 0) {
            throw new IllegalStateException("Modelo não treinado. Chame fit() primeiro.");
        }

        int[] votes = new int[numClasses];
        for (DecisionTree tree : trees) {
            votes[tree.predict(features)]++;
        }

        return (votes[0] >= votes[1]) ? 0 : 1;
    }

    /**
     * Prediz a classe e preenche o array de probabilidades.
     *
     * @param features Vetor de features
     * @param probs    Array de tamanho 2 para receber as probabilidades [P(LEGIT), P(CHEATER)]
     * @return Classe predita (0 = LEGIT, 1 = CHEATER)
     */
    public int predict(double[] features, double[] probs) {
        if (trees == null || trees.length == 0) {
            throw new IllegalStateException("Modelo não treinado. Chame fit() primeiro.");
        }

        int[] votes = new int[numClasses];
        for (DecisionTree tree : trees) {
            votes[tree.predict(features)]++;
        }

        int total = votes[0] + votes[1];
        if (total > 0) {
            probs[0] = (double) votes[0] / total;
            probs[1] = (double) votes[1] / total;
        } else {
            probs[0] = 0.5;
            probs[1] = 0.5;
        }

        return (votes[0] >= votes[1]) ? 0 : 1;
    }

    /**
     * Verifica se o modelo foi treinado.
     */
    public boolean isTrained() {
        return trees != null && trees.length > 0;
    }

    // ═══════════════════════════════════════════════════════════
    //  Decision Tree (CART — Classification And Regression Tree)
    // ═══════════════════════════════════════════════════════════

    /**
     * Árvore de decisão CART para classificação binária.
     */
    private static class DecisionTree implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Node root;

        /**
         * Treina a árvore com os dados fornecidos.
         */
        void fit(double[][] data, int[] labels, int maxDepth,
                 int minSamples, int maxFeatures, Random rand) {
            int n = data.length;
            int[] indices = new int[n];
            for (int i = 0; i < n; i++) {
                indices[i] = i;
            }
            this.root = buildTree(data, labels, indices, 0,
                    maxDepth, minSamples, maxFeatures, rand);
        }

        /**
         * Prediz a classe para um vetor de features.
         */
        int predict(double[] features) {
            Node node = root;
            while (node != null && !node.isLeaf) {
                SplitNode split = (SplitNode) node;
                if (features[split.featureIndex] <= split.threshold) {
                    node = split.left;
                } else {
                    node = split.right;
                }
            }
            return (node != null) ? node.predictedClass : 0;
        }

        /**
         * Constrói recursivamente a árvore.
         */
        private Node buildTree(double[][] data, int[] labels, int[] indices,
                                int depth, int maxDepth, int minSamples,
                                int maxFeatures, Random rand) {
            // Condições de parada
            if (depth >= maxDepth || indices.length < minSamples || isPure(labels, indices)) {
                return new LeafNode(majorityClass(labels, indices));
            }

            // Encontra o melhor split
            SplitResult best = findBestSplit(data, labels, indices, maxFeatures, rand);

            // Se não encontrou split útil, vira folha
            if (best == null || best.improvement <= 0) {
                return new LeafNode(majorityClass(labels, indices));
            }

            // Divide os índices baseado no threshold
            int[] leftIndices = new int[indices.length];
            int[] rightIndices = new int[indices.length];
            int leftCount = 0;
            int rightCount = 0;

            for (int idx : indices) {
                if (data[idx][best.feature] <= best.threshold) {
                    leftIndices[leftCount++] = idx;
                } else {
                    rightIndices[rightCount++] = idx;
                }
            }

            // Se um dos lados ficou vazio, vira folha
            if (leftCount == 0 || rightCount == 0) {
                return new LeafNode(majorityClass(labels, indices));
            }

            int[] leftTrimmed = Arrays.copyOf(leftIndices, leftCount);
            int[] rightTrimmed = Arrays.copyOf(rightIndices, rightCount);

            SplitNode node = new SplitNode(best.feature, best.threshold);
            node.left = buildTree(data, labels, leftTrimmed, depth + 1,
                    maxDepth, minSamples, maxFeatures, rand);
            node.right = buildTree(data, labels, rightTrimmed, depth + 1,
                    maxDepth, minSamples, maxFeatures, rand);
            return node;
        }

        /**
         * Verifica se todas as labels são iguais.
         */
        private boolean isPure(int[] labels, int[] indices) {
            if (indices.length <= 1) return true;
            int first = labels[indices[0]];
            for (int i = 1; i < indices.length; i++) {
                if (labels[indices[i]] != first) return false;
            }
            return true;
        }

        /**
         * Retorna a classe majoritária.
         */
        private int majorityClass(int[] labels, int[] indices) {
            int count0 = 0;
            for (int idx : indices) {
                if (labels[idx] == 0) count0++;
            }
            int count1 = indices.length - count0;
            return (count0 >= count1) ? 0 : 1;
        }

        /**
         * Encontra o melhor split usando Gini impurity.
         */
        private SplitResult findBestSplit(double[][] data, int[] labels,
                                           int[] indices, int maxFeatures, Random rand) {
            int nFeatures = data[0].length;
            int nSamples = indices.length;

            // Seleciona features aleatórias para considerar
            int[] featureSubset;
            if (maxFeatures >= nFeatures) {
                featureSubset = new int[nFeatures];
                for (int i = 0; i < nFeatures; i++) featureSubset[i] = i;
            } else {
                // Seleciona maxFeatures aleatoriamente
                featureSubset = new int[maxFeatures];
                boolean[] used = new boolean[nFeatures];
                for (int i = 0; i < maxFeatures; i++) {
                    int f;
                    do {
                        f = rand.nextInt(nFeatures);
                    } while (used[f]);
                    used[f] = true;
                    featureSubset[i] = f;
                }
            }

            double parentGini = computeGini(labels, indices);
            SplitResult best = null;

            for (int feature : featureSubset) {
                // Encontra thresholds candidatos para esta feature
                double[] thresholds = getUniqueSortedValues(data, labels, indices, feature);

                if (thresholds.length == 0) continue;

                // Para cada threshold, calcula o ganho
                for (double threshold : thresholds) {
                    int leftCount = 0;
                    int rightCount = 0;

                    for (int idx : indices) {
                        if (data[idx][feature] <= threshold) {
                            leftCount++;
                        } else {
                            rightCount++;
                        }
                    }

                    if (leftCount == 0 || rightCount == 0) continue;

                    // Computa Gini dos filhos
                    double giniLeft = 0, giniRight = 0;

                    // Left child
                    int leftLabel1 = 0;
                    for (int idx : indices) {
                        if (data[idx][feature] <= threshold && labels[idx] == 1) {
                            leftLabel1++;
                        }
                    }
                    double pLeft1 = (double) leftLabel1 / leftCount;
                    double pLeft0 = 1.0 - pLeft1;
                    giniLeft = 1.0 - pLeft0 * pLeft0 - pLeft1 * pLeft1;

                    // Right child
                    int rightLabel1 = 0;
                    for (int idx : indices) {
                        if (data[idx][feature] > threshold && labels[idx] == 1) {
                            rightLabel1++;
                        }
                    }
                    double pRight1 = (double) rightLabel1 / rightCount;
                    double pRight0 = 1.0 - pRight1;
                    giniRight = 1.0 - pRight0 * pRight0 - pRight1 * pRight1;

                    // Gini ponderado
                    double weightedGini = (leftCount * giniLeft + rightCount * giniRight) / nSamples;
                    double improvement = parentGini - weightedGini;

                    if (best == null || improvement > best.improvement) {
                        best = new SplitResult(feature, threshold, improvement);
                    }
                }
            }

            return best;
        }

        /**
         * Retorna valores únicos ordenados de uma feature para os índices especificados.
         */
        private double[] getUniqueSortedValues(double[][] data, int[] labels,
                                                int[] indices, int feature) {
            double[] values = new double[indices.length];
            for (int i = 0; i < indices.length; i++) {
                values[i] = data[indices[i]][feature];
            }
            Arrays.sort(values);

            // Remove duplicatas
            int unique = 0;
            for (int i = 0; i < values.length; i++) {
                if (i == 0 || values[i] != values[i - 1]) {
                    values[unique++] = values[i];
                }
            }

            return Arrays.copyOf(values, unique);
        }

        /**
         * Computa o índice Gini para um conjunto de labels.
         */
        private double computeGini(int[] labels, int[] indices) {
            int count0 = 0;
            for (int idx : indices) {
                if (labels[idx] == 0) count0++;
            }
            int count1 = indices.length - count0;
            double p0 = (double) count0 / indices.length;
            double p1 = (double) count1 / indices.length;
            return 1.0 - p0 * p0 - p1 * p1;
        }

        // ── Classes internas da árvore ──

        /**
         * Nó base da árvore de decisão.
         */
        private abstract static class Node implements Serializable {
            @Serial
            private static final long serialVersionUID = 1L;
            boolean isLeaf;
            int predictedClass;
        }

        /**
         * Nó folha (contém a classe predita).
         */
        private static class LeafNode extends Node {
            @Serial
            private static final long serialVersionUID = 1L;

            LeafNode(int predictedClass) {
                this.isLeaf = true;
                this.predictedClass = predictedClass;
            }
        }

        /**
         * Nó de split (contém feature, threshold e filhos).
         */
        private static class SplitNode extends Node {
            @Serial
            private static final long serialVersionUID = 1L;

            int featureIndex;
            double threshold;
            Node left;
            Node right;

            SplitNode(int featureIndex, double threshold) {
                this.isLeaf = false;
                this.featureIndex = featureIndex;
                this.threshold = threshold;
            }
        }

        /**
         * Resultado de um split candidato.
         */
        private static class SplitResult {
            int feature;
            double threshold;
            double improvement;

            SplitResult(int feature, double threshold, double improvement) {
                this.feature = feature;
                this.threshold = threshold;
                this.improvement = improvement;
            }
        }
    }
}
