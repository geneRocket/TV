package com.fongmi.android.tv.ui.dialog;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import okhttp3.HttpUrl;

/** Usable on TV without a browser: the same public page can be opened by scanning its QR code. */
public final class SubtitleWebsiteDialog {
    private SubtitleWebsiteDialog() {}
    public static void show(Context context, String url) {
        if (url == null || HttpUrl.parse(url) == null) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context, R.style.SubtitleDialogColors);
        Context themed = builder.getContext();
        LinearLayout content = new LinearLayout(themed);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20); content.setPadding(padding,padding,padding,padding);
        android.util.DisplayMetrics metrics = themed.getResources().getDisplayMetrics();
        int size = Math.max(1, Math.min(200, (int) (metrics.heightPixels / metrics.density / 4)));
        ImageView code = new ImageView(themed); code.setImageBitmap(QRCode.getBitmap(url,size,2));
        code.setContentDescription(context.getString(R.string.subtitle_qr_code));
        content.addView(code,new LinearLayout.LayoutParams(-1,ResUtil.dp2px(size)));
        TextView address = new TextView(themed); address.setText(url); address.setTextIsSelectable(true); content.addView(address);
        TextView hint = new TextView(themed); hint.setText(R.string.subtitle_website_hint); content.addView(hint);
        ScrollView scroll = new ScrollView(themed);
        scroll.addView(content);
        androidx.appcompat.app.AlertDialog website = builder.setTitle(R.string.subtitle_open_website).setView(scroll)
                .setNegativeButton(R.string.dialog_negative,null)
                .setPositiveButton(R.string.subtitle_open_website,null).create();
        website.setOnShowListener(dialog -> website.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); website.dismiss(); }
            catch (ActivityNotFoundException error) { Notify.show(R.string.subtitle_no_browser); }
        }));
        website.show();
    }
}
