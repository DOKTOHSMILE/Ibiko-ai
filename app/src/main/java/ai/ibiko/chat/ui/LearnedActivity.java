package ai.ibiko.chat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.ibiko.ibiko1.cognition.LearningRecords;

/**
 * Memory › What Ibiko learned: every learned record (cognition/LearningRecords) with how far it is verified, where it
 * came from, and why Ibiko believes it (what said the same, what it replaced); the person confirms it, corrects it (the
 * correction replaces it, the old kept in its history) or forgets it. The page is res/layout/activity_learned.xml, each
 * record res/layout/item_learned_record.xml; this wires them up.
 */
public class LearnedActivity extends AppCompatActivity {
    private LearningRecords records;
    private LinearLayout list;
    private TextView summary;
    private int filter = ai.ibiko.R.id.learned_all;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        setContentView(ai.ibiko.R.layout.activity_learned);
        records = ai.ibiko.ibiko1.Store.learningRecords(new File(getFilesDir(), "brain"));
        list = findViewById(ai.ibiko.R.id.learned_list);
        summary = findViewById(ai.ibiko.R.id.learned_summary);
        findViewById(ai.ibiko.R.id.learned_close).setOnClickListener(v -> finish());
        ((RadioGroup) findViewById(ai.ibiko.R.id.learned_filter)).setOnCheckedChangeListener((g, id) -> {
            filter = id;
            fill();
        });
        Ui.fitInsets(findViewById(ai.ibiko.R.id.learned_root));
        fill();
    }

    /** Whether a record belongs under the chosen filter. */
    static boolean shows(LearningRecords.Record r, String filter) {
        switch (filter) {
            case "unsure":
                return r.status == LearningRecords.Verification.unverified || r.status == LearningRecords.Verification.corroborated;
            case "disputed":
                return r.status == LearningRecords.Verification.disputed;
            case "known":
                return r.status == LearningRecords.Verification.verified || r.status == LearningRecords.Verification.user_confirmed;
            case "yours":
                return "user".equals(r.scope);
            case "replaced":
                return r.status == LearningRecords.Verification.superseded;
            default:
                return r.status != LearningRecords.Verification.superseded;
        }
    }

    private String filterName() {
        if (filter == ai.ibiko.R.id.learned_unsure) {
            return "unsure";
        }
        if (filter == ai.ibiko.R.id.learned_disputed) {
            return "disputed";
        }
        if (filter == ai.ibiko.R.id.learned_known) {
            return "known";
        }
        if (filter == ai.ibiko.R.id.learned_yours) {
            return "yours";
        }
        if (filter == ai.ibiko.R.id.learned_replaced) {
            return "replaced";
        }
        return "all";
    }

    private void fill() {
        list.removeAllViews();
        List<LearningRecords.Record> all = records.all();
        int known = 0, unsure = 0, disputed = 0;
        for (LearningRecords.Record r : all) {
            known += r.status == LearningRecords.Verification.verified || r.status == LearningRecords.Verification.user_confirmed ? 1 : 0;
            unsure += r.status == LearningRecords.Verification.unverified || r.status == LearningRecords.Verification.corroborated ? 1 : 0;
            disputed += r.status == LearningRecords.Verification.disputed ? 1 : 0;
        }
        summary.setText(all.isEmpty() ? "Nothing learned yet. What you confirm or correct, and what Ibiko learns in the Learning chat, shows here."
                : String.format(Locale.US, "%d known · %d unsure · %d disputed. Confirm what's right, correct what isn't.", known, unsure, disputed));
        List<LearningRecords.Record> shown = new ArrayList<LearningRecords.Record>();
        String f = filterName();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (shows(all.get(i), f)) {
                shown.add(all.get(i)); // newest first
            }
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final LearningRecords.Record r : shown) {
            View row = inflater.inflate(ai.ibiko.R.layout.item_learned_record, list, false);
            ((TextView) row.findViewById(ai.ibiko.R.id.record_status)).setText(status(r));
            ((TextView) row.findViewById(ai.ibiko.R.id.record_kind)).setText(kind(r));
            ((TextView) row.findViewById(ai.ibiko.R.id.record_content)).setText(r.content);
            ((TextView) row.findViewById(ai.ibiko.R.id.record_why)).setText(why(r));
            View confirm = row.findViewById(ai.ibiko.R.id.record_confirm), correct = row.findViewById(ai.ibiko.R.id.record_correct);
            boolean replaced = r.status == LearningRecords.Verification.superseded;
            confirm.setVisibility(replaced || r.status == LearningRecords.Verification.user_confirmed ? View.GONE : View.VISIBLE);
            correct.setVisibility(replaced ? View.GONE : View.VISIBLE);
            confirm.setOnClickListener(v -> {
                records.confirm(r.id);
                fill();
            });
            correct.setOnClickListener(v -> correct(r));
            row.findViewById(ai.ibiko.R.id.record_forget).setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setMessage("Forget this? Ibiko won't use it again.")
                    .setPositiveButton("Forget", (d, w) -> {
                        records.forget(r.id);
                        fill();
                    }).setNegativeButton("Keep", null).show());
            list.addView(row);
        }
        if (shown.isEmpty() && !all.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("Nothing here.");
            none.setTextColor(getColor(ai.ibiko.R.color.text_secondary));
            none.setPadding(8, 24, 8, 8);
            list.addView(none);
        }
    }

    private void correct(final LearningRecords.Record r) {
        final EditText box = new EditText(this);
        box.setText(r.content);
        box.setSelection(r.content.length());
        new android.app.AlertDialog.Builder(this).setTitle("What is right?").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    records.correct(r.id, box.getText().toString(), System.currentTimeMillis());
                    fill();
                }).setNegativeButton("Cancel", null).show();
    }

    /** How far it is verified, in plain words. */
    static String status(LearningRecords.Record r) {
        switch (r.status) {
            case verified: return "Known";
            case user_confirmed: return "You confirmed";
            case corroborated: return "Two sources agree";
            case disputed: return "Disputed";
            case superseded: return "Replaced";
            default: return "Unsure";
        }
    }

    /** What kind of thing it is and where it came from. */
    static String kind(LearningRecords.Record r) {
        String from;
        switch (r.sourceType) {
            case "explicit_user_correction": from = "you corrected it"; break;
            case "user_confirmation": from = "you confirmed an answer"; break;
            case "user_statement": from = "you said it"; break;
            case "user_assertion": from = "you said it"; break;
            case "api_model": from = "the API model (" + (r.sourceRef.isEmpty() ? "Learning chat" : r.sourceRef) + ")"; break;
            case "verified_source": from = "checked against what Ibiko holds"; break;
            case "built_in": from = "Ibiko's built-in reference"; break;
            case "research": from = "read online"; break;
            default: from = r.sourceType.replace('_', ' '); break;
        }
        String what = r.category == LearningRecords.Category.preference ? "Your preference" : r.category == LearningRecords.Category.correction
                ? "Correction" : Character.toUpperCase(r.category.name().charAt(0)) + r.category.name().substring(1).replace('_', ' ');
        return what + " · from " + from + " · " + new java.text.SimpleDateFormat("d MMM yyyy", Locale.US).format(new java.util.Date(r.observedAt));
    }

    /** Why it is believed: how sure, what else said it, what it replaced or what replaced it. */
    String why(LearningRecords.Record r) {
        List<String> parts = new ArrayList<String>();
        parts.add(String.format(Locale.US, "%d%% sure", Math.round(r.confidence * 100)));
        if (!r.alsoFrom.isEmpty()) {
            parts.add("also said by " + String.join(", ", r.alsoFrom));
        }
        if (r.supersedes != null) {
            LearningRecords.Record old = records.find(r.supersedes);
            if (old != null) {
                parts.add("replaces “" + (old.content.length() > 80 ? old.content.substring(0, 80) + "…" : old.content) + "”");
            }
        }
        if (r.supersededBy != null) {
            LearningRecords.Record now = records.find(r.supersededBy);
            if (now != null) {
                parts.add("replaced by “" + (now.content.length() > 80 ? now.content.substring(0, 80) + "…" : now.content) + "”");
            }
        }
        return String.join(" · ", parts);
    }
}
