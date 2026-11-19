package ai.ibiko;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w320dp-h640dp-xhdpi")
/** On the narrowest common phone (320 dp) the mic and Send stay whole and on screen. */
public class ComposerFitTest {
    @Test
    public void micAndSendFitANarrowPhone() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View send = c.get().findViewById(R.id.btn_send);
        android.view.View voice = c.get().findViewById(R.id.btn_voice);
        int[] at = new int[2];
        send.getLocationOnScreen(at);
        int screen = c.get().getResources().getDisplayMetrics().widthPixels;
        float d = c.get().getResources().getDisplayMetrics().density;
        org.junit.Assert.assertEquals(40f, send.getWidth() / d, 0.5f);
        org.junit.Assert.assertEquals(40f, voice.getWidth() / d, 0.5f);
        org.junit.Assert.assertTrue("Send ends on screen", at[0] + send.getWidth() <= screen);
        c.pause().stop().destroy();
    }
}
