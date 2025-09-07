package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.DiscreteObjectiveFunction;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;

	/**
     * Builds an objective function with binary alphabet and a custom maximum frequency.
     * @param fap the problem instance
     */
    public final class FAPObjectiveFunctionDirect extends DiscreteObjectiveFunction implements FAPObjectiveFunction {

        private final FrequencyAssignmentProblem fap;
        private final List<String> emitterOrder;     // fixed order consistent with genotype rows
        private final String[] emitterIds;           // faster than List#get in hot loops
        private final int maxFreq;                   // inclusive upper bound for frequency values
        private final int freqCount;                 // = maxFreq + 1

        private final int emitterCount;
        private final int[] demandByIdx;
        private final int[] baseByIdx;               // e -> base index in genotype
        private final int[] offsetByIdx;             // e -> start offset in scratch buffer
        private final int   totalDemand;             // sum(demandByIdx)

        // Pre-sized capacities to avoid HashMap/HashSet resizes
        private final int   mapCapacity;
        private final int[] setCapByIdx;

        // Per-thread scratch to avoid per-evaluation allocations
        private final ThreadLocal<int[]> tlCounts;   // length = emitterCount
        private final ThreadLocal<int[]> tlBuf;      // length = totalDemand (stores found freqs per emitter, packed)

        /**
         * Builds an objective function with binary alphabet and a custom maximum frequency.
         * @param fap the problem instance
         */
        public FAPObjectiveFunctionDirect(FrequencyAssignmentProblem fap) {
            // Cantidad de emisores multiplicado por la frecuencia máxima si se usara cada frecuencia una sola vez, Alfabeto binario.
            super(fap.numEmitters() * (fap.maxFrequency() + 1), 2);
            this.fap = fap;
            this.maxFreq = fap.maxFrequency();
            this.freqCount = maxFreq + 1;

            // Emisores
            this.emitterOrder = new ArrayList<>(fap.getEmitterNames());
            this.emitterCount = emitterOrder.size();
            this.emitterIds = emitterOrder.toArray(new String[0]);

            // Indexing helpers + demands
            this.baseByIdx = new int[emitterCount];
            this.demandByIdx = new int[emitterCount];
            int sum = 0;
            for (int e = 0; e < emitterCount; e++) {
                baseByIdx[e] = e * freqCount;
                demandByIdx[e] = fap.getEmitter(emitterIds[e]).demand();
                sum += demandByIdx[e];
            }
            this.totalDemand = sum;

            // Packed offsets for the scratch buffer (one contiguous area per emitter)
            this.offsetByIdx = new int[emitterCount];
            int running = 0;
            for (int e = 0; e < emitterCount; e++) {
                offsetByIdx[e] = running;
                running += demandByIdx[e];
            }

            // Map/set capacities (avoid rehashes)
            this.mapCapacity = Math.max(16, (int) (emitterCount / 0.75f) + 1);
            this.setCapByIdx = new int[emitterCount];
            for (int e = 0; e < emitterCount; e++) {
                int d = demandByIdx[e];
                // small floor capacity keeps HashSet fast, avoids resize; ≥ ceil(d/0.75)
                this.setCapByIdx[e] = Math.max(4, (int) (d / 0.75f) + 1);
            }

            // Thread-local scratch: counts per emitter, plus a flat buffer for all assigned freqs
            this.tlCounts = ThreadLocal.withInitial(() -> new int[this.emitterCount]);
            this.tlBuf    = ThreadLocal.withInitial(() -> new int[Math.max(1, this.totalDemand)]);
        }

        @Override
        public FrequencyAssignmentProblem getProblemData() {
            return fap;
        }
        public int getMaxFreq() { return maxFreq; }
        public List<String> getEmitterOrder() { return Collections.unmodifiableList(emitterOrder); }

        @Override
        public OptimizationSense getOptimizationSense() {
            return OptimizationSense.MINIMIZATION;
        }

        /**
         * Convertir el genotipo en un objeto (escaneo único por emisor hasta cubrir la demanda).
         * Nota: Este método puede ser llamado desde fuera de _evaluate, por eso no usa los buffers internos.
         */
        @Override
        public Map<String, Set<Integer>> genotype2map(Genotype g) {
            Map<String, Set<Integer>> assignment = new HashMap<>(mapCapacity);
            for (int e = 0; e < emitterCount; e++) {
                final int demand = demandByIdx[e];
                final String id = emitterIds[e];
                if (demand == 0) {
                    assignment.put(id, Collections.emptySet());
                    continue;
                }
                HashSet<Integer> freqs = new HashSet<>(setCapByIdx[e]);
                final int base = baseByIdx[e];
                int found = 0;
                for (int f = 0; f < freqCount && found < demand; f++) {
                    if ((int) g.getGene(base + f) != 0) {
                        freqs.add(f);
                        found++;
                    }
                }
                assignment.put(id, freqs);
            }
            return assignment;
        }

        /**
         * Evaluar las soluciones.
         * Penalización alta si no se cumple cualquier restricción dura.
         */
        @Override
        protected final double _evaluate(Individual i) {
            // Obtener el genoma
            final Genotype g = i.getGenome();

            // Scratch (reutilizado por hilo)
            final int[] counts = tlCounts.get();
            java.util.Arrays.fill(counts, 0);
            final int[] buf = tlBuf.get(); // packed freqs for all emitters (size = totalDemand)

            // PASS 1: validar demanda por emisor + obtener span global y guardar freqs sin reasignar estructuras
            int globalMin = freqCount; // sentinel: no usado aún
            int globalMax = -1;

            for (int e = 0; e < emitterCount; e++) {
                final int demand = demandByIdx[e];
                if (demand == 0) continue;

                final int base   = baseByIdx[e];
                final int offset = offsetByIdx[e];

                int c = 0;
                for (int f = 0; f < freqCount; f++) {
                    if ((int) g.getGene(base + f) != 0) {
                        if (c == demand) {
                            // más asignaciones de las permitidas
                            return Double.MAX_VALUE; // (faster+correct than maxFreq ^ 2)
                        }
                        buf[offset + c] = f; // guardar frecuencia encontrada
                        c++;
                        if (f < globalMin) globalMin = f;
                        if (f > globalMax) globalMax = f;
                    }
                }
                if (c != demand) {
                    // faltan asignaciones para este emisor
                    return Double.MAX_VALUE;
                }
                counts[e] = c;
            }

            // PASS 2: construir el assignment justo ahora (muchos individuos ya habrán salido)
            Map<String, Set<Integer>> assignment = new HashMap<>(mapCapacity);
            for (int e = 0; e < emitterCount; e++) {
                final int demand = demandByIdx[e];
                final String id = emitterIds[e];
                if (demand == 0) {
                    assignment.put(id, Collections.emptySet());
                    continue;
                }
                final int offset = offsetByIdx[e];
                HashSet<Integer> set = new HashSet<>(setCapByIdx[e]);
                for (int k = 0; k < demand; k++) {
                    set.add(buf[offset + k]);
                }
                assignment.put(id, set);
            }

            // Chequeo de factibilidad (potencialmente costoso)
            if (!fap.isFeasible(assignment)) {
                return Double.MAX_VALUE;
            }

            // Span ya calculado; si nadie asignó nada, span = 0
            return (globalMax >= 0 ? (globalMax - globalMin) : 0);
        }
    }


/*      // Si no hay frecuencias asignadas, no es una asignación factible
        // Como todas las penalizaciones se aplican igual, se puede cerrar el bucle más rápidamente para ahorrar tiempo.
        // Si no es factible por otras razones
        // Si es factible, devolver la distancia entre la frecuencia máxima y la mínima
 */
