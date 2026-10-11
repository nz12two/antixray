package com.antixray.plugin.learning;

import com.antixray.plugin.learning.Fingerprint.Label;
import com.antixray.plugin.learning.DetectorConsensus.ConsensusResult;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

public class ThreeTierStore {
    private final Deque<Fingerprint> candidateDB = new ConcurrentLinkedDeque<>();
    private final Deque<Fingerprint> verifiedDB = new ConcurrentLinkedDeque<>();
    private final List<FingerprintSummary> referenceDB = Collections.synchronizedList(new ArrayList<>());

    private final Object tierLock = new Object();

    private final int maxCandidates = 15_000;
    private final int maxVerified = 25_000;
    private final int maxReference = 300;
    private final long verifiedTtl = 365L * 24 * 3600_000;
    private final int quarantineConfirmations = 3;

    private final SimilarityEngine similarityEngine = new SimilarityEngine();

    public enum AdmitResult {
        ACCEPTED_CANDIDATE, PROMOTED_VERIFIED, REJECTED, INSUFFICIENT_EVIDENCE
    }

    public AdmitResult admit(Fingerprint fp, double riskScore, RiskHistory riskHistory,
                              DetectorConsensus consensus, DetectorReputation reputation) {
        ConfidenceBand band = ConfidenceBand.forScore(riskScore);

        if (band == ConfidenceBand.DISCARD) {
            return AdmitResult.REJECTED;
        }

        if (band == ConfidenceBand.STATS_ONLY) {
            return AdmitResult.INSUFFICIENT_EVIDENCE;
        }

        if (band == ConfidenceBand.TEMPORARY && !riskHistory.isSustainedAbove(fp.playerId(), 70, 600_000)) {
            return AdmitResult.INSUFFICIENT_EVIDENCE;
        }

        if (band == ConfidenceBand.PERMANENT && !riskHistory.isSustainedAbove(fp.playerId(), 90, 1_200_000)) {
            return AdmitResult.INSUFFICIENT_EVIDENCE;
        }

        ConsensusResult cr = consensus.evaluate(fp.detectorScores(), 3);
        if (!cr.hasConsensus()) {
            return AdmitResult.INSUFFICIENT_EVIDENCE;
        }

        if (!cr.hasDiversity()) {
            return AdmitResult.INSUFFICIENT_EVIDENCE;
        }

        if (!reputation.isReliable("WallTracking") && fp.detectorScores().getOrDefault("WallTracking", 0d) > 80) {
            return AdmitResult.INSUFFICIENT_EVIDENCE;
        }

        synchronized (tierLock) {
            candidateDB.add(fp);
            trim(candidateDB, maxCandidates);
        }

        return AdmitResult.ACCEPTED_CANDIDATE;
    }

    public void processQuarantine(RiskHistory riskHistory) {
        List<Fingerprint> toPromote = new ArrayList<>();

        synchronized (tierLock) {
            for (Fingerprint candidate : candidateDB) {
                if (canPromote(candidate, riskHistory)) {
                    toPromote.add(candidate);
                }
            }

            for (Fingerprint fp : toPromote) {
                candidateDB.remove(fp);
                verifiedDB.add(fp.withLabel(Label.POSITIVE));
                trim(verifiedDB, maxVerified);
            }
        }

        summarizeOldVerified();
    }

    private boolean canPromote(Fingerprint candidate, RiskHistory riskHistory) {
        long age = System.currentTimeMillis() - candidate.sessionEnd();
        if (age < 3600_000) return false;

        int similarSessions = 0;
        for (Fingerprint other : candidateDB) {
            if (other.sessionId().equals(candidate.sessionId())) continue;
            if (other.playerId().equals(candidate.playerId())) {
                double sim = similarityEngine.compare(candidate, other).finalScore();
                if (sim >= 0.80) similarSessions++;
            }
        }

        return similarSessions >= quarantineConfirmations;
    }

    private void summarizeOldVerified() {
        long cutoff = System.currentTimeMillis() - verifiedTtl;
        List<Fingerprint> oldVerified = verifiedDB.stream()
                .filter(fp -> fp.sessionEnd() > 0 && fp.sessionEnd() < cutoff)
                .collect(Collectors.toList());

        if (oldVerified.size() < 10) return;

        Map<String, List<Fingerprint>> byLabel = oldVerified.stream()
                .collect(Collectors.groupingBy(f -> f.label().name()));

        for (var entry : byLabel.entrySet()) {
            if (entry.getValue().size() >= 10) {
                FingerprintSummary summary = FingerprintSummary.fromFingerprints(entry.getKey(), entry.getValue());
                referenceDB.add(summary);
                verifiedDB.removeAll(entry.getValue());
            }
        }

        while (referenceDB.size() > maxReference) {
            referenceDB.sort(Comparator.comparingLong(FingerprintSummary::maxTimestamp));
            referenceDB.remove(0);
        }
    }

    public double bestPositiveSimilarity(Fingerprint fp) {
        double best = 0;

        for (Fingerprint ref : verifiedDB) {
            if (ref.label() != Label.POSITIVE) continue;
            double s = similarityEngine.compare(fp, ref).finalScore();
            if (s > best) best = s;
        }

        if (best < 0.9) {
            for (Fingerprint ref : candidateDB) {
                if (ref.label() == Label.NEGATIVE) continue;
                double s = similarityEngine.compare(fp, ref).finalScore();
                if (s > best) best = s;
            }
        }

        if (best < 0.7) {
            for (FingerprintSummary summary : referenceDB) {
                if (!summary.label().equals("POSITIVE")) continue;
                double s = summary.similarityTo(fp);
                if (s > best) best = s;
            }
        }

        return best;
    }

    public double bestNegativeSimilarity(Fingerprint fp) {
        double best = 0;

        for (Fingerprint ref : verifiedDB) {
            if (ref.label() != Label.NEGATIVE) continue;
            double s = similarityEngine.compare(fp, ref).finalScore();
            if (s > best) best = s;
        }

        if (best < 0.7) {
            for (FingerprintSummary summary : referenceDB) {
                if (!summary.label().equals("NEGATIVE")) continue;
                double s = summary.similarityTo(fp);
                if (s > best) best = s;
            }
        }

        return best;
    }

    public void labelPlayerFingerprints(UUID playerId, Label label) {
        synchronized (tierLock) {
            List<Fingerprint> updated = new ArrayList<>();

            Iterator<Fingerprint> it = candidateDB.iterator();
            while (it.hasNext()) {
                Fingerprint fp = it.next();
                if (fp.playerId().equals(playerId)) {
                    updated.add(fp.withLabel(label));
                    it.remove();
                }
            }

            verifiedDB.addAll(updated);
            trim(verifiedDB, maxVerified);
        }
    }

    public boolean isCandidate(UUID sessionId) {
        return candidateDB.stream().anyMatch(f -> f.sessionId().equals(sessionId));
    }

    public int candidateCount() { return candidateDB.size(); }
    public int verifiedCount() { return verifiedDB.size(); }
    public int referenceCount() { return referenceDB.size(); }
    public int totalCount() { return candidateDB.size() + verifiedDB.size(); }

    public String storageReport() {
        long estimate = (candidateDB.size() + verifiedDB.size()) * 512L
                + referenceDB.stream().mapToLong(FingerprintSummary::storageEstimate).sum();
        return String.format("Candidate=%d Verified=%d Reference=%d (~%dKB)",
                candidateDB.size(), verifiedDB.size(), referenceDB.size(), estimate / 1024);
    }

    private void trim(Deque<Fingerprint> deque, int max) {
        while (deque.size() > max) deque.pollLast();
    }
}
