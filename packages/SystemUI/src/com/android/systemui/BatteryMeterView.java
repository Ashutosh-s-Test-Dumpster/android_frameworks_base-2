/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui;

import static android.provider.Settings.System.SHOW_BATTERY_PERCENT;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import android.widget.TextView;
import com.android.settingslib.graph.BatteryMeterDrawableBase;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.text.NumberFormat;

public class BatteryMeterView extends LinearLayout implements
        BatteryStateChangeCallback, Tunable, DarkReceiver, ConfigurationListener {

    private final BatteryMeterDrawableBase mDrawable;
    private final String mSlotBattery;
    private final ImageView mBatteryIconView;
    private TextView mBatteryPercentView;

    private BatteryController mBatteryController;
    private SettingObserver mSettingObserver;
    private int mTextColor;
    private int mLevel;
    private boolean mForceShowPercent;

    public BatteryMeterView(Context context) {
        this(context, null, 0);
    }

    public BatteryMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryMeterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);

        TypedArray atts = context.obtainStyledAttributes(attrs, R.styleable.BatteryMeterView,
                defStyle, 0);
        final int frameColor = atts.getColor(R.styleable.BatteryMeterView_frameColor,
                context.getColor(R.color.batterymeter_frame_color));
        mDrawable = new BatteryMeterDrawableBase(context, frameColor);
        atts.recycle();

        mSettingObserver = new SettingObserver(new Handler(context.getMainLooper()));

        mSlotBattery = context.getString(
                com.android.internal.R.string.status_bar_battery);
        mBatteryIconView = new ImageView(context);
        mBatteryIconView.setImageDrawable(mDrawable);
        final MarginLayoutParams mlp = new MarginLayoutParams(
                getResources().getDimensionPixelSize(R.dimen.status_bar_battery_icon_width),
                getResources().getDimensionPixelSize(R.dimen.status_bar_battery_icon_height));
        mlp.setMargins(0, 0, 0,
                getResources().getDimensionPixelOffset(R.dimen.battery_margin_bottom));
        addView(mBatteryIconView, mlp);

        updateShowPercent();
        // Init to not dark at all.
        onDarkChanged(new Rect(), 0, DarkIconDispatcher.DEFAULT_ICON_TINT);
    }

    public void setForceShowPercent(boolean show) {
        mForceShowPercent = show;
        updateShowPercent();
    }

    // StatusBarIconController reaches in here and adjusts the layout parameters of the icon
    public ImageView getBatteryIconView() {
        return mBatteryIconView;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (StatusBarIconController.ICON_BLACKLIST.equals(key)) {
            ArraySet<String> icons = StatusBarIconController.getIconBlacklist(newValue);
            setVisibility(icons.contains(mSlotBattery) ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mBatteryController = Dependency.get(BatteryController.class);
        mBatteryController.addCallback(this);
        getContext().getContentResolver().registerContentObserver(
                Settings.System.getUriFor(SHOW_BATTERY_PERCENT), false, mSettingObserver);
        updateShowPercent();
        Dependency.get(TunerService.class).addTunable(this, StatusBarIconController.ICON_BLACKLIST);
        Dependency.get(ConfigurationController.class).addCallback(this);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBatteryController.removeCallback(this);
        getContext().getContentResolver().unregisterContentObserver(mSettingObserver);
        Dependency.get(TunerService.class).removeTunable(this);
        Dependency.get(ConfigurationController.class).removeCallback(this);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        mDrawable.setBatteryLevel(level);
        mDrawable.setPluggedIn(pluggedIn);
        mLevel = level;
        updatePercentText();
        setContentDescription(
                getContext().getString(charging ? R.string.accessibility_battery_level_charging
                        : R.string.accessibility_battery_level, level));
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        mDrawable.setPowerSave(isPowerSave);
    }

    private TextView loadPercentView() {
        return (TextView) LayoutInflater.from(getContext())
                .inflate(R.layout.battery_percentage_view, null);
    }

    private void updatePercentText() {
        if (mBatteryPercentView != null) {
            mBatteryPercentView.setText(
                    NumberFormat.getPercentInstance().format(mLevel/100f));
        }
    }

    private void updateShowPercent() {
        final boolean showing = mBatteryPercentView != null;
        if (0 != Settings.System.getInt(getContext().getContentResolver(),
                SHOW_BATTERY_PERCENT, 0) || mForceShowPercent) {
            if (!showing) {
                mBatteryPercentView = loadPercentView();
                if (mTextColor != 0) mBatteryPercentView.setTextColor(mTextColor);
                updatePercentText();
                addView(mBatteryPercentView,
                        0,
                        new ViewGroup.LayoutParams(
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.MATCH_PARENT));
            }
        } else {
            if (showing) {
                removeView(mBatteryPercentView);
                mBatteryPercentView = null;
            }
        }
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        scaleBatteryMeterViews();
    }

    /**
     * Looks up the scale factor for status bar icons and scales the battery view by that amount.
     */
    private void scaleBatteryMeterViews() {
        Resources res = getContext().getResources();
        TypedValue typedValue = new TypedValue();

        res.getValue(R.dimen.status_bar_icon_scale_factor, typedValue, true);
        float iconScaleFactor = typedValue.getFloat();

        int batteryHeight = res.getDimensionPixelSize(R.dimen.status_bar_battery_icon_height);
        int batteryWidth = res.getDimensionPixelSize(R.dimen.status_bar_battery_icon_width);
        int marginBottom = res.getDimensionPixelSize(R.dimen.battery_margin_bottom);

        LinearLayout.LayoutParams scaledLayoutParams = new LinearLayout.LayoutParams(
                (int) (batteryWidth * iconScaleFactor), (int) (batteryHeight * iconScaleFactor));
        scaledLayoutParams.setMargins(0, 0, 0, marginBottom);

        mBatteryIconView.setLayoutParams(scaledLayoutParams);
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mDrawable.setDarkIntensity(DarkIconDispatcher.isInArea(area, this) ? darkIntensity : 0);
        setTextColor(DarkIconDispatcher.getTint(area, this, tint));
    }

    public void setTextColor(int color) {
        mTextColor = color;
        if (mBatteryPercentView != null) {
            mBatteryPercentView.setTextColor(color);
        }
    }

    public void setRawColors(int fgColor, int bgColor) {
        mDrawable.setColors(fgColor, bgColor);
    }

    private final class SettingObserver extends ContentObserver {
        public SettingObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            updateShowPercent();
        }
    }
}
