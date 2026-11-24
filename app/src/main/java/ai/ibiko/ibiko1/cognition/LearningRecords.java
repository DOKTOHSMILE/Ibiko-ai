package ai.ibiko.ibiko1.cognition;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.Json;

/**
 * Engine 6, active learning and knowledge consolidation (priority 6): what Ibiko learns is kept as records, one JSON
 * line each ({@code brain/learning.jsonl}), in the specification's form: what was learned, its category and scope,
 * where it came from and when, how sure, how far it is verified, what it supersedes, when it expires. Nothing is
 * promoted just because it was said: a fact from one unverified statement stays unverified (or "user provided"); two
 * independent sources make it corroborated; only a trusted source or the person's own confirmation verifies it. A
 * preference is the person's, not a fact about the world. A correction supersedes the old record, which is kept with
 * its history. Secrets are not stored. Pure Java.
 *
 * <pre>
 * L1  candidate   captured with category, scope, source type and reference, time, confidence, tags
 * L2  duplicate   the same content again: from another source it is corroborated; from the same, only counted
 * L3  conflict    the same thing with another value: a correction supersedes the old (kept, marked superseded);
 *                 anything else is kept beside it as disputed
 * L4  promote     unverified → corroborated (2 independent sources) → verified (a trusted source, or the person
 *                 confirmed it); a user's factual claim alone is never "verified" world knowledge
 * L5  scope       a preference is the person's (scope "user"); project notes "project"; world knowledge "general"
 * L6  private     passwords, card numbers, keys and codes are refused unless the person asked for them to be kept
 * L7  care        expiry, forgetting one record, and why it is believed (its history)
 * </pre>
 */
public final class LearningRecords {
    public enum Category { definition, fact, procedure, reasoning_rule, correction, preference, task_outcome, hypothesis }

    public enum Verification { unverified, corroborated, verified, user_confirmed, disputed, superseded }

    /** One learned record. */
    public static final class Record {
        public final String id, scope, content, sourceType, sourceRef;
        public final Category category;
        public final long observedAt;
        public double confidence;
        public Verification status;
        public String supersedes, supersededBy;
        public long expiresAt;
        public final List<String> tags = new ArrayList<String>();
        /** Each other source that said the same thing. */
        public final List<String> alsoFrom = new ArrayList<String>();

        Record(String id, Category category, String scope, String content, String sourceType, String sourceRef, long observedAt, double confidence,
                Verification status) {
            this.id = id;
            this.category = category;
            this.scope = scope;
            this.content = content;
            this.sourceType = sourceType;
            this.sourceRef = sourceRef;
            this.observedAt = observedAt;
            this.confidence = confidence;
            this.status = status;
        }

        Map<String, Object> map() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("category", category.name());
            m.put("scope", scope);
            m.put("content", content);
            m.put("source_type", sourceType);
            m.put("source_ref", sourceRef);
            m.put("observed_at", iso(observedAt));
            m.put("confidence", Math.round(confidence * 100) / 100.0);
            m.put("verification_status", status.name());
            m.put("supersedes", supersedes);
            m.put("superseded_by", supersededBy);
            m.put("expires_at", expiresAt <= 0 ? null : iso(expiresAt));
            m.put("tags", new ArrayList<Object>(tags));
            m.put("also_from", new ArrayList<Object>(alsoFrom));
            return m;
        }

        /** The record as one JSON line. */
        public String json() {
            return Json.write(map());
        }
    }

    /** Source types that verify a fact on their own: a trusted reference, or the person confirming it. */
    private static final Pattern TRUSTED = Pattern.compile("built_in|verified_source|user_confirmation|explicit_user_correction");
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(?:password|passcode|pin(?: code)?|cvv|otp|one[- ]time code|secret key|private key|api key|"
            + "seed phrase|recovery phrase|bvn)\\b|\\b(?:\\d[ -]?){13,19}\\b|\\bsk-[A-Za-z0-9]{16,}|\\bAKIA[0-9A-Z]{16}\\b|\\bghp_[A-Za-z0-9]{20,}");

    private final File file;
    private final List<Record> all = new ArrayList<Record>();
    private int next = 1;

    public LearningRecords(File file) {
        this.file = file;
        load();
    }

    /** L6: whether a text holds something private (a password, a card number, a key). */
    public static boolean secret(String text) {
        return text != null && SECRET.matcher(text).find();
    }

    /**
     * L1–L5: a candidate learned. {@code key} names what it is about ("capital of anambra"), so a later record about the
     * same thing is found; {@code sourceType} is one of explicit_user_correction, user_confirmation, user_assertion,
     * built_in, verified_source, api_model, research, task_outcome. Null when it is not kept (a secret, or empty).
     */
    public synchronized Record learn(Category category, String key, String content, String sourceType, String sourceRef, double confidence,
            long now, boolean secretAllowed, String... tags) {
        if (content == null || content.trim().isEmpty() || secret(content) && !secretAllowed) {
            return null;
        }
        String scope = category == Category.preference ? "user" : sourceType.startsWith("project") ? "project" : "general";
        String k = key == null ? norm(content) : norm(key);
        // L2: the same content again
        for (Record r : all) {
            if (r.status != Verification.superseded && norm(r.content).equals(norm(content))) {
                if (!r.sourceRef.equals(sourceRef) && !r.alsoFrom.contains(sourceRef)) {
                    r.alsoFrom.add(sourceRef);
                    if (r.status == Verification.unverified || r.status == Verification.disputed) {
                        r.status = Verification.corroborated; // L4: two independent sources
                        r.confidence = Math.max(r.confidence, Math.min(0.85, confidence + 0.15));
                    }
                }
                if (TRUSTED.matcher(sourceType).matches() && r.status != Verification.verified && r.status != Verification.user_confirmed) {
                    r.status = sourceType.startsWith("user") || sourceType.startsWith("explicit") ? Verification.user_confirmed : Verification.verified;
                }
                save();
                return r;
            }
        }
        Verification status;
        if (category == Category.preference) {
            status = Verification.user_confirmed; // the person's own word about what they want: theirs, not a world fact
        } else if (TRUSTED.matcher(sourceType).matches()) {
            status = sourceType.startsWith("user") || sourceType.startsWith("explicit") ? Verification.user_confirmed : Verification.verified;
        } else if (category == Category.hypothesis) {
            status = Verification.unverified;
        } else {
            status = Verification.unverified; // L4: one statement, not checked: never more than this
        }
        Record rec = new Record("learn_" + String.format(Locale.US, "%06d", next++), category, scope, content.trim(), sourceType, sourceRef == null ? "" : sourceRef,
                now, Math.max(0, Math.min(1, status == Verification.unverified ? Math.min(confidence, 0.6) : confidence)), status);
        for (String t : tags) {
            rec.tags.add(t);
        }
        rec.tags.add("key:" + k);
        // L3: the same thing, another value
        for (Record r : all) {
            if (r.status == Verification.superseded || !r.tags.contains("key:" + k) || !r.scope.equals(rec.scope)) {
                continue;
            }
            if (sourceType.equals("explicit_user_correction") || category == Category.correction
                    || Evidence.reliability(rank(sourceType)) > Evidence.reliability(rank(r.sourceType))) {
                r.status = Verification.superseded;
                r.supersededBy = rec.id;
                rec.supersedes = r.id;
            } else {
                rec.status = Verification.disputed;
                if (r.status == Verification.unverified) {
                    r.status = Verification.disputed;
                }
            }
        }
        all.add(rec);
        save();
        return rec;
    }

    private static ai.ibiko.ibiko1.formulas.Truth.Source rank(String sourceType) {
        switch (sourceType) {
            case "explicit_user_correction":
            case "user_confirmation":
                return ai.ibiko.ibiko1.formulas.Truth.Source.CONFIRMED;
            case "built_in":
                return ai.ibiko.ibiko1.formulas.Truth.Source.BUILT_IN;
            case "verified_source":
            case "research":
                return ai.ibiko.ibiko1.formulas.Truth.Source.SOURCE;
            case "user_assertion":
                return ai.ibiko.ibiko1.formulas.Truth.Source.LEARNED;
            default:
                return ai.ibiko.ibiko1.formulas.Truth.Source.MODEL;
        }
    }

    /** An answer the person said was wrong: the record of it, if any, is disputed (kept, not trusted). */
    public synchronized boolean dispute(String content) {
        boolean any = false;
        for (Record r : all) {
            if (r.status != Verification.superseded && norm(r.content).equals(norm(content))) {
                r.status = Verification.disputed;
                r.confidence = Math.min(r.confidence, 0.3);
                any = true;
            }
        }
        if (any) {
            save();
        }
        return any;
    }

    /** The person confirmed a record: it is theirs to vouch for now (user confirmed), held with full confidence. */
    public synchronized boolean confirm(String id) {
        Record r = find(id);
        if (r == null || r.status == Verification.superseded) {
            return false;
        }
        r.status = Verification.user_confirmed;
        r.confidence = Math.max(r.confidence, 0.95);
        save();
        return true;
    }

    /** What a record is about (the key it was filed under), so a correction supersedes it; null when it has none. */
    public static String keyOf(Record r) {
        for (String t : r.tags) {
            if (t.startsWith("key:")) {
                return t.substring(4);
            }
        }
        return null;
    }

    /**
     * The person corrected a record: the right content is kept as a correction that supersedes it (the old one kept,
     * with its history). The new record, or null when the record is gone or the correction is empty or private.
     */
    public synchronized Record correct(String id, String rightContent, long now) {
        Record r = find(id);
        if (r == null || rightContent == null || rightContent.trim().isEmpty()) {
            return null;
        }
        String key = keyOf(r);
        Record fixed = learn(Category.correction, key == null ? r.content : key, rightContent.trim(), "explicit_user_correction", "memory", 0.95, now,
                false, "correction");
        if (fixed == r) {
            return r; // the same words again: that is a confirmation, kept as one
        }
        if (fixed != null && r.status != Verification.superseded) {
            r.status = Verification.superseded; // a different key would not have caught it
            r.supersededBy = fixed.id;
            fixed.supersedes = r.id;
            save();
        }
        return fixed;
    }

    /** The records in use: not superseded, not expired. */
    public synchronized List<Record> active(long now) {
        List<Record> out = new ArrayList<Record>();
        for (Record r : all) {
            if (r.status != Verification.superseded && (r.expiresAt <= 0 || r.expiresAt > now)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Every record, history included. */
    public synchronized List<Record> all() {
        return new ArrayList<Record>(all);
    }

    /** L7: why a record is believed: it, and each record it superseded, newest first. */
    public synchronized List<Record> history(String id) {
        List<Record> out = new ArrayList<Record>();
        Record r = find(id);
        while (r != null) {
            out.add(r);
            r = r.supersedes == null ? null : find(r.supersedes);
        }
        return out;
    }

    /** L7: a record forgotten (and nothing points to it any more). */
    public synchronized boolean forget(String id) {
        Record r = find(id);
        if (r == null) {
            return false;
        }
        all.remove(r);
        for (Record o : all) {
            if (id.equals(o.supersedes)) {
                o.supersedes = null;
            }
            if (id.equals(o.supersededBy)) {
                o.supersededBy = null;
                o.status = o.status == Verification.superseded ? Verification.disputed : o.status;
            }
        }
        save();
        return true;
    }

    /** L7: records past their expiry are dropped; how many. */
    public synchronized int expire(long now) {
        int before = all.size();
        all.removeIf(r -> r.expiresAt > 0 && r.expiresAt <= now);
        if (all.size() != before) {
            save();
        }
        return before - all.size();
    }

    public synchronized Record find(String id) {
        for (Record r : all) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
    }

    static String iso(long t) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date(t));
    }

    static long time(Object iso) {
        if (!(iso instanceof String)) {
            return 0;
        }
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse((String) iso).getTime();
        } catch (java.text.ParseException e) {
            return 0;
        }
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Map<String, Object> m = Json.obj(Json.parse(line));
                    Record r = new Record(Json.str(m.get("id"), ""), Category.valueOf(Json.str(m.get("category"), "fact")), Json.str(m.get("scope"), "general"),
                            Json.str(m.get("content"), ""), Json.str(m.get("source_type"), ""), Json.str(m.get("source_ref"), ""), time(m.get("observed_at")),
                            Json.num(m.get("confidence"), 0.5), Verification.valueOf(Json.str(m.get("verification_status"), "unverified")));
                    r.supersedes = m.get("supersedes") instanceof String ? (String) m.get("supersedes") : null;
                    r.supersededBy = m.get("superseded_by") instanceof String ? (String) m.get("superseded_by") : null;
                    r.expiresAt = time(m.get("expires_at"));
                    r.tags.addAll(Json.strings(m.get("tags")));
                    r.alsoFrom.addAll(Json.strings(m.get("also_from")));
                    all.add(r);
                    try {
                        next = Math.max(next, Integer.parseInt(r.id.replaceAll("\\D", "")) + 1);
                    } catch (NumberFormatException ignored) {
                        // an id of another form
                    }
                } catch (RuntimeException ignored) {
                    // a line it can't read is skipped, not the file
                }
            }
        } catch (IOException ignored) {
            // starts fresh
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Record r : all) {
            b.append(r.json()).append('\n');
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) {
                Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
                tmp.delete();
            }
        } catch (IOException ignored) {
            // kept for this run
        }
    }
}
