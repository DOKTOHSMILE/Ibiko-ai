package ai.ibiko.chat.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import ai.ibiko.social.campaign.Catalog;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.Product;

/**
 * The business's products: each with its photo, name, price and a few words, saved once and used by every campaign
 * (the real photo in the pictures, the real price in posts and replies). Without {@link #EXTRA_ID} the list; with
 * it, one product to add or change.
 */
public final class CatalogActivity extends AppCompatActivity {
    static final String EXTRA_ID = "product";
    private static final int PICK = 7;
    /** The longest side a product photo is kept at (a campaign picture is 1080 across). */
    static final int PHOTO_SIDE = 1080;

    private LinearLayout body;
    private Catalog catalog;
    private Product editing;
    private ImageView photoView;
    private File pickedPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        catalog = CampaignRunner.catalog(this);
        String id = getIntent().getStringExtra(EXTRA_ID);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 4));
        ImageView close = Ui.icon(this, ai.ibiko.R.drawable.ic_close, Ui.ACCENT, ai.ibiko.IconSize.STANDARD);
        close.setContentDescription("Close");
        int cp = Ui.dp(this, 12);
        close.setPadding(cp, cp, cp, cp);
        close.setOnClickListener(v -> finish());
        Ui.press(close);
        head.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        TextView title = Ui.text(this, id == null ? "Products" : id.isEmpty() ? "New product" : "Product", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 48), 1));
        root.addView(head);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        body.setPadding(pad, 0, pad, Ui.dp(this, 24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (id != null) {
            editing = id.isEmpty() ? null : catalog.get(id);
            if (editing == null) {
                editing = new Product();
                editing.id = "p" + System.currentTimeMillis();
            }
            root.addView(editFoot(pad));
        }
        setContentView(root);
        Ui.fitInsets(root);
        if (id != null) {
            editForm();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editing == null) {
            list(); // back from a product
        }
    }

    private void list() {
        body.removeAllViews();
        body.addView(Ui.text(this, "Add what you sell once. Campaigns show the real photo in their pictures and use the"
                + " real price in posts and replies, so neither is made up.", 14, Ui.SUB, false));
        TextView add = Ui.button(this, "Add a product", true);
        add.setOnClickListener(v -> startActivity(new Intent(this, CatalogActivity.class).putExtra(EXTRA_ID, "")));
        body.addView(add, Ui.wide(this, 16));
        for (final Product p : catalog.all()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            ImageView pic = new ImageView(this);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pic.setBackground(Ui.round(this, Ui.FIELD, 0, 12));
            pic.setClipToOutline(true);
            File f = catalog.photoFile(p.id);
            if (f.isFile()) {
                pic.setImageBitmap(thumb(f, Ui.dp(this, 56)));
            }
            pic.setContentDescription(p.name);
            row.addView(pic, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(Ui.dp(this, 12), 0, 0, 0);
            words.addView(Ui.text(this, p.name, 15, Ui.TEXT, true));
            String under = (p.price.isEmpty() ? "No price" : p.price) + (p.about.isEmpty() ? "" : " · " + p.about.replaceAll("\\s+", " "));
            TextView u = Ui.text(this, under, 13, Ui.SUB, false);
            u.setMaxLines(2);
            u.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(u);
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
            Ui.press(row);
            row.setOnClickListener(v -> startActivity(new Intent(this, CatalogActivity.class).putExtra(EXTRA_ID, p.id)));
            body.addView(row);
        }
    }

    private EditText name, price, about;

    private void editForm() {
        body.removeAllViews();
        photoView = new ImageView(this);
        photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photoView.setBackground(Ui.round(this, Ui.FIELD, 0, 16));
        photoView.setClipToOutline(true);
        photoView.setContentDescription("Product photo. Tap to choose one");
        File f = catalog.photoFile(editing.id);
        if (f.isFile()) {
            photoView.setImageBitmap(thumb(f, Ui.dp(this, 240)));
        }
        photoView.setOnClickListener(v -> pickPhoto());
        body.addView(photoView, new LinearLayout.LayoutParams(-1, Ui.dp(this, 220)));
        TextView pick = Ui.text(this, f.isFile() ? "Change the photo" : "Choose a photo", 14, Ui.ACCENT, false);
        pick.setMinHeight(Ui.dp(this, 48));
        pick.setGravity(Gravity.CENTER);
        pick.setOnClickListener(v -> pickPhoto());
        body.addView(pick);
        name = field("Name", "E.g. Rechargeable stun torch", editing.name, 1);
        price = field("Price", "E.g. ₦18,000", editing.price, 1);
        about = field("About it", "What it does, sizes or colours, what's in the box, delivery", editing.about, 4);
    }

    private EditText field(String label, String hint, String value, int lines) {
        EditText e = Ui.field(this, hint, value, false, lines > 1);
        if (lines > 1) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(lines);
            e.setMaxLines(10);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }
        e.setId(android.view.View.generateViewId());
        TextView l = Ui.text(this, label, 13, Ui.SUB, true);
        l.setLabelFor(e.getId());
        body.addView(l, Ui.wide(this, 16));
        body.addView(e, Ui.wide(this, 6));
        return e;
    }

    private LinearLayout editFoot(int pad) {
        LinearLayout foot = new LinearLayout(this);
        foot.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 12));
        TextView remove = Ui.button(this, "Delete", false);
        remove.setOnClickListener(v -> {
            try {
                catalog.remove(editing.id);
            } catch (IOException ignored) {
                // the list shows what was kept
            }
            finish();
        });
        TextView save = Ui.button(this, "Save", true);
        save.setOnClickListener(v -> save());
        foot.addView(remove, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 2f);
        sl.leftMargin = Ui.dp(this, 12);
        foot.addView(save, sl);
        return foot;
    }

    private void save() {
        editing.name = name.getText().toString().trim();
        editing.price = price.getText().toString().trim();
        editing.about = about.getText().toString().trim();
        if (editing.name.isEmpty()) {
            name.setError("Give it a name");
            name.requestFocus();
            return;
        }
        if (pickedPhoto != null && pickedPhoto.isFile()) {
            File to = catalog.photoFile(editing.id);
            to.getParentFile().mkdirs();
            if (!pickedPhoto.renameTo(to)) {
                Toast.makeText(this, "The photo couldn't be kept.", Toast.LENGTH_SHORT).show();
            }
        }
        editing.photo = catalog.photoFile(editing.id).isFile() ? catalog.photoFile(editing.id).getName() : "";
        try {
            catalog.put(editing);
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "Not saved: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pickPhoto() {
        Intent pick = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
        try {
            startActivityForResult(pick, PICK);
        } catch (android.content.ActivityNotFoundException e) {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PICK);
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final File out = new File(getCacheDir(), "product-" + editing.id + ".jpg");
        new Thread(() -> {
            boolean ok = shrink(uri, out);
            runOnUiThread(() -> {
                if (!ok) {
                    Toast.makeText(this, "That photo couldn't be read.", Toast.LENGTH_SHORT).show();
                    return;
                }
                pickedPhoto = out;
                photoView.setImageBitmap(thumb(out, Ui.dp(this, 240)));
            });
        }, "ibiko-product-photo").start();
    }

    /** The picked photo, at most {@link #PHOTO_SIDE} on its longest side, saved as a JPEG. */
    private boolean shrink(Uri uri, File out) {
        try {
            BitmapFactory.Options size = new BitmapFactory.Options();
            size.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, size);
            }
            if (size.outWidth <= 0 || size.outHeight <= 0) {
                return false;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            while (Math.max(size.outWidth, size.outHeight) / (o.inSampleSize * 2) >= PHOTO_SIDE) {
                o.inSampleSize *= 2;
            }
            Bitmap b;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, o);
            }
            if (b == null) {
                return false;
            }
            float k = PHOTO_SIDE / (float) Math.max(b.getWidth(), b.getHeight());
            if (k < 1f) {
                Bitmap s = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
                b.recycle();
                b = s;
            }
            try (FileOutputStream fo = new FileOutputStream(out)) {
                b.compress(Bitmap.CompressFormat.JPEG, 85, fo);
            }
            b.recycle();
            return out.length() > 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** A small copy of a photo for the screen. */
    static Bitmap thumb(File f, int side) {
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), size);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 1;
        while (Math.max(size.outWidth, size.outHeight) / (o.inSampleSize * 2) >= side) {
            o.inSampleSize *= 2;
        }
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
