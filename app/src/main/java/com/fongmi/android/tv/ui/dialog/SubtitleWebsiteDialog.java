package com.fongmi.android.tv.ui.dialog;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20); content.setPadding(padding,padding,padding,padding);
        TextView hint = new TextView(context); hint.setText(R.string.subtitle_website_hint); content.addView(hint);
        ImageView code = new ImageView(context); code.setImageBitmap(QRCode.getBitmap(url,200,1));
        content.addView(code,new LinearLayout.LayoutParams(-1,ResUtil.dp2px(220)));
        TextView address = new TextView(context); address.setText(url); address.setTextIsSelectable(true); content.addView(address);
        androidx.appcompat.app.AlertDialog website = new MaterialAlertDialogBuilder(context).setTitle(R.string.subtitle_open_website).setView(content)
                .setNegativeButton(R.string.dialog_negative,null)
                .setPositiveButton(R.string.subtitle_open_website,null).create();
        website.setOnShowListener(dialog -> website.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); website.dismiss(); }
            catch (ActivityNotFoundException error) { Notify.show(R.string.subtitle_no_browser); }
        }));
        website.show();
    }
}
