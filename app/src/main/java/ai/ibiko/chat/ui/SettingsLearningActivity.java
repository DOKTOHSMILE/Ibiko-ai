package ai.ibiko.chat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import ai.ibiko.llm.LocalModel;
import ai.ibiko.llm.ModelHub;

/** Settings \u203A Ibiko 1 and learning: its own screen and layout (activity_settings_learning.xml), opened from the list of sections. */
public class SettingsLearningActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_learning;
    }

    @Override
    protected String sectionTitle() {
        return "Ibiko 1 and learning";
    }

    @Override
    protected void buildSection() {
            // ---- Ibiko 1, the work in the chat, and actions (in step with the model menu and the tools sheet)
            LinearLayout own = group("Ibiko 1 and learning", "The brain in use, the learning schedule, training file");
            String pick = prefs.getString("brain_pick", "api");
            own.addView(Ui.text(this, "Answering now: " + ("ibiko".equals(pick) ? ai.ibiko.ibiko1.Ibiko1.NAME : "device".equals(pick)
                    ? "the offline model" : "your API models") + ". Change it from the model name under the chat's title.", 14, Ui.SUB, false));
            final ai.ibiko.ibiko1.LearningSchedule.Plan plan = ai.ibiko.ibiko1.LearningJob.plan(this);
            own.addView(toggle("Learn on a schedule", (plan.on ? "On, " : "Hours: ") + plan.words() + ". " + ai.ibiko.ibiko1.Ibiko1.NAME
                    + " goes online and learns from what you asked. Set the hours from the model menu.", "learn_schedule_on", plan.on,
                    on -> ai.ibiko.ibiko1.LearningJob.setPlan(this, new ai.ibiko.ibiko1.LearningSchedule.Plan(on, plan.start, plan.end,
                            plan.wifiOnly, plan.chargingOnly, plan.most))), Ui.wide(this, 10));
            own.addView(toggle("Ask what you mean before answering", "When " + ai.ibiko.ibiko1.Ibiko1.NAME + " doesn't know something,"
                    + " it looks it up, asks you to explain in your own words, looks that up too, compares the two, and answers"
                    + " with “I may be wrong”, so you can correct it.", ai.ibiko.ibiko1.Ibiko1.ASK_BACK, true, null), Ui.wide(this, 10));
            own.addView(Ui.text(this, ai.ibiko.ibiko1.Ibiko1.NAME + " learns from every message, every model's answer, what it researches"
                    + " and the social pages it reads (language only: people's words are never taken as facts). "
                    + ai.ibiko.ibiko1.Ibiko1.scoreLine(getFilesDir()) + ". See it under Memory.", 13, Ui.SUB, false), Ui.wide(this, 10));
            android.view.View learnNow = Ui.button(this, "Learn now", false);
            learnNow.setOnClickListener(v -> {
                ai.ibiko.ibiko1.LearningJob.learnNow(this);
                android.widget.Toast.makeText(this, "Learning now. The Workload sheet shows it.", android.widget.Toast.LENGTH_SHORT).show();
            });
            own.addView(learnNow, Ui.wide(this, 10));
            android.view.View train = Ui.button(this, "Export Ibiko 1's training file", false);
            train.setOnClickListener(v -> exportTraining());
            own.addView(train, Ui.wide(this, 8));
            own.addView(Ui.text(this, "Everything Ibiko 1 learned (confirmed answers, facts, words) as a JSONL file in Downloads/Ibiko,"
                    + " for training a model of your own.", 13, Ui.SUB, false), Ui.wide(this, 6));

            // ---- imported knowledge: reviewed datasets (a dictionary, relations) that every model is given for the
            // words in a message
            LinearLayout kb = group("Imported knowledge", "Word lists and relations from datasets you add");
            knowledgeSummary = Ui.text(this, "", 13, Ui.SUB, false);
            kb.addView(knowledgeSummary, Ui.wide(this, 6));
            fillKnowledge();
            kb.addView(toggle("Ibiko is a commercial product", "On: only datasets with an open licence (MIT, Apache, CC BY, CC0)"
                    + " can be added. Off: non-commercial ones (CC BY-NC) can too.", COMMERCIAL, true, null), Ui.wide(this, 10));
            android.view.View add = Ui.button(this, "Add a dataset (CSV or TSV)", false);
            add.setOnClickListener(v -> {
                android.content.Intent choose = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                choose.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                choose.setType("*/*");
                choose.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/tab-separated-values",
                        "text/comma-separated-values", "text/plain"});
                startActivityForResult(choose, PICK_DATASET);
            });
            kb.addView(add, Ui.wide(this, 10));
            kb.addView(Ui.text(this, "A word list needs the columns lemma (or word) and language, and may have definition and pos."
                    + " Relations need subject, predicate and object. You say the licence; nothing is downloaded.", 13, Ui.SUB, false),
                    Ui.wide(this, 6));
            android.view.View clear = Ui.button(this, "Remove imported knowledge", false);
            clear.setOnClickListener(v -> confirm("Remove every imported dataset? What Ibiko 1 learned from you stays.", () ->
                    new Thread(() -> {
                        store().clearImported();
                        runOnUiThread(this::fillKnowledge);
                    }, "ibiko-knowledge").start()));
            kb.addView(clear, Ui.wide(this, 8));

    }

    static final int PICK_DATASET = 7301;
    /** Settings: whether Ibiko is a commercial product, which decides the licences a dataset may have. */
    public static final String COMMERCIAL = "knowledge_commercial";
    private TextView knowledgeSummary;
    private ai.ibiko.intelligence.StructuredKnowledgeDatabase store;

    private synchronized ai.ibiko.intelligence.StructuredKnowledgeDatabase store() {
        if (store == null) {
            store = new ai.ibiko.intelligence.StructuredKnowledgeDatabase(this);
        }
        return store;
    }

    /** "1,204 words and 40 relations from Afri-Dict (open)" or "None yet". */
    private void fillKnowledge() {
        if (knowledgeSummary == null) {
            return;
        }
        new Thread(() -> {
            String said;
            try {
                long[] n = store().counts();
                java.util.List<String> sets = store().datasets();
                said = n[2] == 0 ? "None yet. Every model is given what an added dataset says about the words in your message."
                        : String.format(java.util.Locale.US, "%,d words and %,d relations from ", n[0], n[1]) + String.join(", ", sets) + ".";
            } catch (RuntimeException e) {
                said = "The knowledge store couldn't be read.";
            }
            final String s = said;
            runOnUiThread(() -> knowledgeSummary.setText(s));
        }, "ibiko-knowledge").start();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_DATASET && result == RESULT_OK && data != null && data.getData() != null) {
            reviewDataset(data.getData());
        }
    }

    /** The person says what the file is and its licence before anything is imported. */
    private void reviewDataset(final android.net.Uri uri) {
        String name = uri.getLastPathSegment() == null ? "dataset" : uri.getLastPathSegment();
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\.(csv|tsv|txt|tab)$", "");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 22);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final android.widget.EditText title = new android.widget.EditText(this);
        title.setHint("Dataset name");
        title.setText(name);
        final android.widget.EditText source = new android.widget.EditText(this);
        source.setHint("Where it came from (a link)");
        box.addView(title);
        box.addView(source);
        box.addView(Ui.text(this, "Its licence", 14, Ui.TEXT, true), Ui.wide(this, 12));
        final android.widget.RadioGroup licence = new android.widget.RadioGroup(this);
        for (int i = 0; i < ai.ibiko.intelligence.importer.DatasetFile.LICENCES.length; i++) {
            android.widget.RadioButton r = new android.widget.RadioButton(this);
            r.setId(View.generateViewId());
            r.setTag(i);
            r.setText(ai.ibiko.intelligence.importer.DatasetFile.LICENCES[i]);
            licence.addView(r);
            if (i == 3) {
                r.setChecked(true);
            }
        }
        box.addView(licence);
        final android.widget.CheckBox checked = new android.widget.CheckBox(this);
        checked.setText("I've read its licence and checked what is in the file");
        box.addView(checked, Ui.wide(this, 8));
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add a dataset")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    View picked = licence.findViewById(licence.getCheckedRadioButtonId());
                    int which = picked == null ? 3 : (Integer) picked.getTag();
                    importDataset(uri, title.getText().toString().trim(), source.getText().toString().trim(), which,
                            checked.isChecked());
                })
                .show();
    }

    private void importDataset(final android.net.Uri uri, final String name, final String source, final int licence,
            final boolean checked) {
        final boolean commercial = prefs.getBoolean(COMMERCIAL, true);
        android.widget.Toast.makeText(this, "Adding " + name + "\u2026", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String said;
            try {
                said = importNow(uri, name.isEmpty() ? "dataset" : name, source, licence, checked, commercial);
            } catch (SecurityException e) {
                said = "Not added: " + e.getMessage();
            } catch (Exception e) {
                said = "Not added: " + (e.getMessage() == null ? "the file couldn't be read" : e.getMessage());
            }
            final String s = said;
            runOnUiThread(() -> {
                new android.app.AlertDialog.Builder(this).setTitle(name).setMessage(s).setPositiveButton("OK", null).show();
                fillKnowledge();
            });
        }, "ibiko-knowledge-import").start();
    }

    private String importNow(android.net.Uri uri, String name, String source, int licence, boolean checked, boolean commercial)
            throws java.io.IOException {
        String first;
        try (java.io.BufferedReader peek = new java.io.BufferedReader(new java.io.InputStreamReader(
                getContentResolver().openInputStream(uri), java.nio.charset.StandardCharsets.UTF_8))) {
            first = peek.readLine();
        }
        char delimiter = ai.ibiko.intelligence.importer.DatasetFile.delimiter(uri.getLastPathSegment(), first);
        ai.ibiko.intelligence.importer.DatasetFile.Kind kind = ai.ibiko.intelligence.importer.DatasetFile.kind(first, delimiter);
        if (kind == ai.ibiko.intelligence.importer.DatasetFile.Kind.UNKNOWN) {
            return "Not added: its first line needs the columns lemma (or word) and language, or subject, predicate and object.";
        }
        ai.ibiko.intelligence.importer.KnowledgeDatasetImporter.Metadata meta = new ai.ibiko.intelligence.importer
                .KnowledgeDatasetImporter.Metadata(name, "", source.isEmpty() ? uri.toString() : source,
                ai.ibiko.intelligence.importer.DatasetFile.LICENCE_NAMES[licence], "",
                ai.ibiko.intelligence.importer.DatasetFile.policy(licence, checked));
        ai.ibiko.intelligence.importer.KnowledgeDatasetImporter importer =
                new ai.ibiko.intelligence.importer.KnowledgeDatasetImporter(store());
        try (java.io.Reader in = new java.io.InputStreamReader(getContentResolver().openInputStream(uri),
                java.nio.charset.StandardCharsets.UTF_8)) {
            ai.ibiko.intelligence.importer.KnowledgeDatasetImporter.Report r =
                    kind == ai.ibiko.intelligence.importer.DatasetFile.Kind.RELATIONS
                            ? importer.importRelations(in, delimiter, meta, commercial, checked)
                            : importer.importLexicon(in, delimiter, meta, commercial);
            return String.format(java.util.Locale.US, "Added %,d %s from %,d rows%s. Every model now gets what it says about"
                    + " the words in your messages.", r.lexemesInserted,
                    kind == ai.ibiko.intelligence.importer.DatasetFile.Kind.RELATIONS ? "relations" : "words", r.rowsRead,
                    r.skipped > 0 ? String.format(java.util.Locale.US, " (%,d rows skipped: a column was empty)", r.skipped) : "");
        }
    }
}