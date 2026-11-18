package ai.ibiko.chat.ui;

import android.widget.LinearLayout;

/** Settings \u203A About: its own screen and layout (activity_settings_about.xml), opened from the list of sections. */
public class SettingsAboutActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_about;
    }

    @Override
    protected String sectionTitle() {
        return "About";
    }

    @Override
    protected void buildSection() {
            // ---- about
            LinearLayout about = card();
            about.addView(Ui.text(this, "Ibiko " + version(), 16, Ui.TEXT, false));
            about.addView(Ui.text(this, "Ibiko, with your models or the one on this device.", 13, Ui.SUB, false), Ui.wide(this, 4));
            body.addView(about, Ui.wide(this, 8));
    }
}
