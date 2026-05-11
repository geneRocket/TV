package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.fongmi.android.tv.utils.KeyUtil;

public class CustomUpDownView extends AppCompatTextView {

    private UpListener upListener;
    private DownListener downListener;

    public CustomUpDownView(@NonNull Context context) {
        super(context);
    }

    public CustomUpDownView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setUpListener(UpListener upListener) {
        this.upListener = upListener;
    }

    public void setDownListener(DownListener downListener) {
        this.downListener = downListener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (upListener != null && KeyUtil.isUpKey(event)) {
                if (upListener.onUp()) return true;
            } else if (downListener != null && KeyUtil.isDownKey(event)) {
                if (downListener.onDown()) return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public interface UpListener {

        boolean onUp();
    }

    public interface DownListener {

        boolean onDown();
    }
}
