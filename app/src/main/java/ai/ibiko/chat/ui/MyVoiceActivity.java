package ai.ibiko.chat.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import ai.ibiko.voice.MyVoice;

/**
 * Settings › Chat › My voice: the pilot reads sentences aloud, one at a time, and Ibiko keeps each good take; the
 * recordings go to the training notebook as one zip, and the voice it trains comes back here to be installed, after
 * which Ibiko speaks with it. Each take is checked as soon as it ends (cut short, too loud, too quiet, too noisy), so a
 * bad one is read again at once rather than found out after training.
 */
public class MyVoiceActivity extends AppCompatActivity {
    static final int PICK_VOICE = 7411, ASK_MIC = 7412;

    private List<MyVoice.Line> lines;
    private int at;
    private TextView progress, number, sentence, verdict, recordButton;
    private android.widget.Switch useMine;
    private volatile boolean recording;
    private Thread recorder;
    private short[] lastTake;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        setContentView(ai.ibiko.R.layout.activity_my_voice); // the page is res/layout/activity_my_voice.xml; this wires it up
        lines = MyVoice.script(readScript());
        progress = findViewById(ai.ibiko.R.id.my_voice_progress);
        number = findViewById(ai.ibiko.R.id.my_voice_number);
        sentence = findViewById(ai.ibiko.R.id.my_voice_sentence);
        verdict = findViewById(ai.ibiko.R.id.my_voice_verdict);
        recordButton = findViewById(ai.ibiko.R.id.my_voice_record);
        useMine = findViewById(ai.ibiko.R.id.my_voice_use);
        findViewById(ai.ibiko.R.id.my_voice_back).setOnClickListener(v -> go(at - 1));
        recordButton.setOnClickListener(v -> toggle());
        findViewById(ai.ibiko.R.id.my_voice_play).setOnClickListener(v -> play());
        findViewById(ai.ibiko.R.id.my_voice_next).setOnClickListener(v -> go(at + 1));
        findViewById(ai.ibiko.R.id.my_voice_share).setOnClickListener(v -> share());
        findViewById(ai.ibiko.R.id.my_voice_add).setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            startActivityForResult(pick, PICK_VOICE);
        });
        for (int id : new int[]{ai.ibiko.R.id.my_voice_back, ai.ibiko.R.id.my_voice_record, ai.ibiko.R.id.my_voice_play,
            ai.ibiko.R.id.my_voice_next, ai.ibiko.R.id.my_voice_share, ai.ibiko.R.id.my_voice_add}) {
            Ui.press(findViewById(id));
        }
        Ui.fitInsets(findViewById(ai.ibiko.R.id.my_voice_root));
        go(firstUnrecorded());
    }

    private String readScript() {
        try (InputStream in = ai.ibiko.ibiko1.TextCode.open(p -> {
            try {
                return getAssets().open(p);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "voice/recording_script.tsv")) {
            if (in == null) {
                return "";
            }
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException | RuntimeException e) {
            return "";
        }
    }

    private int firstUnrecorded() {
        Map<String, String> done = MyVoice.metadata(getFilesDir());
        for (int i = 0; i < lines.size(); i++) {
            if (!done.containsKey(lines.get(i).id)) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private void go(int i) {
        if (lines.isEmpty() || recording) {
            return;
        }
        at = Math.max(0, Math.min(lines.size() - 1, i));
        MyVoice.Line l = lines.get(at);
        number.setText("Sentence " + (at + 1) + " of " + lines.size() + (MyVoice.wav(getFilesDir(), l.id).isFile() ? " · recorded ✓" : ""));
        sentence.setText(l.text);
        verdict.setText(MyVoice.wav(getFilesDir(), l.id).isFile() ? "Tap Record to read it again, or Next." : "Tap Record, read the sentence, then tap Stop.");
        verdict.setTextColor(getColor(ai.ibiko.R.color.text_secondary));
        lastTake = null;
        refresh();
    }

    private void refresh() {
        int done = MyVoice.metadata(getFilesDir()).size();
        double minutes = MyVoice.minutes(getFilesDir());
        progress.setText(String.format(java.util.Locale.US, "%d of %d recorded · %.1f minutes (aim for 45)", done, lines.size(), minutes));
        boolean installed = ai.ibiko.voice.VoicePacks.installed(getFilesDir(), ai.ibiko.voice.VoicePacks.MY_VOICE);
        useMine.setEnabled(installed);
        useMine.setOnCheckedChangeListener(null);
        useMine.setChecked(MyVoice.chosen(getFilesDir()));
        useMine.setOnCheckedChangeListener((b, on) -> {
            MyVoice.choose(getFilesDir(), on);
            refresh();
        });
    }

    private void toggle() {
        if (recording) {
            recording = false; // the recorder thread finishes the take
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, ASK_MIC);
            return;
        }
        start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == ASK_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            start();
        }
    }

    private void start() {
        // checked here, where the microphone is opened: the permission can be taken back at any time in Settings
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, ASK_MIC);
            return;
        }
        final MyVoice.Line line = lines.get(at);
        final int rate = MyVoice.RATE;
        int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        final AudioRecord rec;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min, rate));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                rec.release();
                verdict.setText("The microphone couldn't be opened. Is another app using it?");
                return;
            }
        } catch (SecurityException e) {
            verdict.setText("Ibiko needs the microphone to record you. Allow it, then tap Record again.");
            return;
        } catch (RuntimeException e) {
            verdict.setText("The microphone couldn't be opened.");
            return;
        }
        recording = true;
        recordButton.setText("Stop");
        verdict.setText("Recording… read the sentence, then tap Stop.");
        verdict.setTextColor(getColor(ai.ibiko.R.color.accent));
        recorder = new Thread(() -> {
            short[] buf = new short[rate / 10];
            java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
            rec.startRecording();
            long started = System.currentTimeMillis();
            while (recording && System.currentTimeMillis() - started < 30_000) {
                int n = rec.read(buf, 0, buf.length);
                for (int i = 0; i < n; i++) {
                    all.write(buf[i] & 0xff);
                    all.write((buf[i] >> 8) & 0xff);
                }
            }
            rec.stop();
            rec.release();
            byte[] b = all.toByteArray();
            short[] pcm = new short[b.length / 2];
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8));
            }
            short[] trimmed = MyVoice.trim(pcm, rate);
            MyVoice.Take take = MyVoice.check(trimmed, rate, line.text.split("\\s+").length);
            String said;
            boolean kept = false;
            if (take.ok()) {
                try {
                    MyVoice.keep(getFilesDir(), line, trimmed, rate);
                    kept = true;
                    said = String.format(java.util.Locale.US, "Kept ✓ (%.1f s)", take.seconds);
                } catch (java.io.IOException e) {
                    said = "It couldn't be saved: " + e.getMessage();
                }
            } else {
                said = take.problem;
            }
            final String s = said;
            final boolean ok = kept;
            lastTake = trimmed;
            runOnUiThread(() -> {
                recording = false;
                recordButton.setText("Record");
                verdict.setText(s);
                verdict.setTextColor(ok ? getColor(ai.ibiko.R.color.accent) : 0xFFE5484D);
                refresh();
                if (ok) {
                    verdict.postDelayed(() -> go(firstUnrecorded() > at ? firstUnrecorded() : at + 1), 900); // on to the next one
                }
            });
        }, "ibiko-my-voice");
        recorder.start();
    }

    private void play() {
        short[] pcm = lastTake;
        if (pcm == null) {
            File w = MyVoice.wav(getFilesDir(), lines.get(at).id);
            try {
                pcm = w.isFile() ? MyVoice.wavSamples(java.nio.file.Files.readAllBytes(w.toPath())) : null;
            } catch (java.io.IOException e) {
                pcm = null;
            }
        }
        if (pcm == null || pcm.length == 0) {
            Toast.makeText(this, "Nothing recorded for this sentence yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        final short[] sound = pcm;
        new Thread(() -> {
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(MyVoice.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(sound.length * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
            t.write(sound, 0, sound.length);
            t.play();
            try {
                Thread.sleep(sound.length * 1000L / MyVoice.RATE + 200);
            } catch (InterruptedException ignored) {
                // stopped
            }
            t.release();
        }, "ibiko-my-voice-play").start();
    }

    private void share() {
        new Thread(() -> {
            try {
                File zip = new File(getCacheDir(), "my-voice-recordings.zip");
                int n;
                try (FileOutputStream out = new FileOutputStream(zip)) {
                    n = MyVoice.export(getFilesDir(), out);
                }
                final int count = n;
                runOnUiThread(() -> {
                    if (count == 0) {
                        Toast.makeText(this, "Record some sentences first.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (MyVoice.minutes(getFilesDir()) < MyVoice.ENOUGH_MINUTES) {
                        Toast.makeText(this, "Shared. More recordings (45 minutes or so) give a better voice.", Toast.LENGTH_LONG).show();
                    }
                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, "ai.ibiko.files", zip);
                    startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share my recordings"));
                });
            } catch (java.io.IOException | RuntimeException e) {
                runOnUiThread(() -> Toast.makeText(this, "The recordings couldn't be packed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "ibiko-my-voice-export").start();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_VOICE || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final android.net.Uri uri = data.getData();
        Toast.makeText(this, "Adding your voice…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String said;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                MyVoice.install(getFilesDir(), in);
                MyVoice.choose(getFilesDir(), true);
                said = "Your voice is in. Ibiko speaks with it from now on.";
            } catch (java.io.IOException | RuntimeException e) {
                said = e.getMessage() == null ? "That file couldn't be added." : e.getMessage();
            }
            final String s = said;
            runOnUiThread(() -> {
                Toast.makeText(this, s, Toast.LENGTH_LONG).show();
                refresh();
            });
        }, "ibiko-my-voice-install").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        recording = false;
    }
}
