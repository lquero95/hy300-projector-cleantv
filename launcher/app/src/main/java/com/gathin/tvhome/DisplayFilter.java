package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.lang.ref.WeakReference;

/**
 * Global illumination, shadow-detail (black-level lift) and colour temperature controller for the HY300 projector.
 * Renders via HomeKeyService's TYPE_ACCESSIBILITY_OVERLAY across all apps and mirrors inside MainActivity.
 */
public final class DisplayFilter {
    private static final String PREFS = "hy300_display";
    private static final String KEY_BRIGHTNESS = "brightness";   // 20..100 (%)
    private static final String KEY_BLACK_LIFT = "black_lift";   // 0..100 (%) lifts crushed dark details
    private static final String KEY_COLOR_TEMP = "color_temp";   // -50 (Warm) .. +50 (Cool)
    private static final String KEY_CONTRAST_WARMTH = "tint";    // -50 (Green) .. +50 (Magenta)
    private static final String KEY_PRESET = "preset";

    public static final String[] PRESET_NAMES = {
            "Claro HY300 (Recomendado)",
            "Cine Cálido",
            "Vívido Frío",
            "Noche Suave",
            "Estándar (Neutro)"
    };

    private static final List<WeakReference<FilterView>> activeViews = new ArrayList<>();

    private DisplayFilter() {}

    public static int getBrightness(Context ctx) {
        return prefs(ctx).getInt(KEY_BRIGHTNESS, 100);
    }

    public static int getBlackLift(Context ctx) {
        return prefs(ctx).getInt(KEY_BLACK_LIFT, 30);
    }

    public static int getColorTemp(Context ctx) {
        return prefs(ctx).getInt(KEY_COLOR_TEMP, 0);
    }

    public static int getTint(Context ctx) {
        return prefs(ctx).getInt(KEY_CONTRAST_WARMTH, 0);
    }

    public static int getPreset(Context ctx) {
        return prefs(ctx).getInt(KEY_PRESET, 0);
    }

    public static void applyPreset(Context ctx, int preset) {
        int p = (preset % PRESET_NAMES.length + PRESET_NAMES.length) % PRESET_NAMES.length;
        int brightness = 100, blackLift = 30, colorTemp = 0, tint = 0;
        switch (p) {
            case 0: // Claro HY300 (Recomendado): max illumination + 30% shadow lift + slight cool clarity
                brightness = 100;
                blackLift = 30;
                colorTemp = 5;
                tint = 0;
                break;
            case 1: // Cine Cálido: max illumination + 25% shadow lift + warm cinema tone
                brightness = 100;
                blackLift = 25;
                colorTemp = -20;
                tint = 5;
                break;
            case 2: // Vívido Frío: high clarity cool daylight + 35% shadow lift
                brightness = 100;
                blackLift = 35;
                colorTemp = 25;
                tint = -5;
                break;
            case 3: // Noche Suave: reduced illumination (65%) + warm comfort
                brightness = 65;
                blackLift = 10;
                colorTemp = -25;
                tint = 0;
                break;
            case 4: // Estándar (Neutro): no filter
                brightness = 100;
                blackLift = 0;
                colorTemp = 0;
                tint = 0;
                break;
        }
        prefs(ctx).edit()
                .putInt(KEY_PRESET, p)
                .putInt(KEY_BRIGHTNESS, brightness)
                .putInt(KEY_BLACK_LIFT, blackLift)
                .putInt(KEY_COLOR_TEMP, colorTemp)
                .putInt(KEY_CONTRAST_WARMTH, tint)
                .apply();
        syncSystemBrightness(ctx, brightness);
        notifyViews();
    }

    public static void setValues(Context ctx, int brightness, int blackLift, int colorTemp, int tint) {
        int b = Math.max(20, Math.min(100, brightness));
        int l = Math.max(0, Math.min(100, blackLift));
        int t = Math.max(-50, Math.min(50, colorTemp));
        int m = Math.max(-50, Math.min(50, tint));
        prefs(ctx).edit()
                .putInt(KEY_BRIGHTNESS, b)
                .putInt(KEY_BLACK_LIFT, l)
                .putInt(KEY_COLOR_TEMP, t)
                .putInt(KEY_CONTRAST_WARMTH, m)
                .apply();
        syncSystemBrightness(ctx, b);
        notifyViews();
    }

    private static void syncSystemBrightness(Context ctx, int brightnessPct) {
        try {
            int val = Math.max(10, Math.min(255, Math.round(brightnessPct * 255f / 100f)));
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, val);
        } catch (Throwable ignored) {
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static FilterView attachToActivity(Activity activity, ViewGroup root) {
        FilterView v = new FilterView(activity);
        v.setClickable(false);
        v.setFocusable(false);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setElevation(100f);
        root.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        registerView(v);
        return v;
    }

    public static FilterView attachSystemOverlay(Context serviceCtx, WindowManager wm) {
        if (wm == null) return null;
        try {
            FilterView v = new FilterView(serviceCtx);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(v, lp);
            registerView(v);
            return v;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void registerView(FilterView v) {
        synchronized (activeViews) {
            activeViews.add(new WeakReference<>(v));
        }
        v.invalidate();
    }

    public static void notifyViews() {
        synchronized (activeViews) {
            Iterator<WeakReference<FilterView>> it = activeViews.iterator();
            while (it.hasNext()) {
                FilterView v = it.next().get();
                if (v == null) {
                    it.remove();
                } else {
                    v.postInvalidate();
                }
            }
        }
    }

    public static final class FilterView extends View {
        private final Paint paint = new Paint();

        public FilterView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Context ctx = getContext();
            int brightness = getBrightness(ctx);
            int blackLift = getBlackLift(ctx);
            int colorTemp = getColorTemp(ctx);
            int tint = getTint(ctx);

            // 1. Black-level / Shadow Detail Lift (raises crushed dark levels on low-lumen projectors)
            if (blackLift > 0) {
                // Up to alpha 46 (~18% luminous veil) at 100% lift
                int alpha = Math.round(blackLift * 0.44f);
                paint.setColor(Color.argb(alpha, 232, 242, 255));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }

            // 2. Colour Temperature (-50 Warm Amber .. +50 Cool Daylight)
            if (colorTemp < 0) {
                int alpha = Math.round((-colorTemp / 50f) * 48f);
                paint.setColor(Color.argb(alpha, 255, 156, 64));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            } else if (colorTemp > 0) {
                int alpha = Math.round((colorTemp / 50f) * 44f);
                paint.setColor(Color.argb(alpha, 88, 168, 255));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }

            // 3. Green / Magenta Tint balance (-50 .. +50)
            if (tint < 0) {
                int alpha = Math.round((-tint / 50f) * 32f);
                paint.setColor(Color.argb(alpha, 90, 230, 150));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            } else if (tint > 0) {
                int alpha = Math.round((tint / 50f) * 32f);
                paint.setColor(Color.argb(alpha, 245, 110, 200));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }

            // 4. Illumination dimming (when brightness < 100%)
            if (brightness < 100) {
                int dimAlpha = Math.round(((100 - brightness) / 80f) * 185f);
                dimAlpha = Math.max(0, Math.min(195, dimAlpha));
                paint.setColor(Color.argb(dimAlpha, 0, 0, 0));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }
        }
    }

    /** Shows the interactive D-pad Illumination & Colour Control dialog in MainActivity. */
    public static PopupWindow showDialog(Activity activity, Runnable onDismissed) {
        final float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        int padV = Math.round(20 * density);
        panel.setPadding(padH, padV, padH, padV);

        GradientDrawable panelBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF7F8FAFC, 0xF5E2E8F0});
        panelBg.setCornerRadius(22 * density);
        panelBg.setStroke(Math.round(1.5f * density), 0xFF94A3B8);
        panel.setBackground(panelBg);

        // Title header
        TextView title = new TextView(activity);
        title.setText("Iluminación y Color del Proyector");
        title.setTextSize(21);
        title.setTextColor(0xFF0F172A);
        title.setTypeface(activity.getResources().getFont(R.font.outfit_medium));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(activity);
        subtitle.setText("Usa ◀ ▶ para ajustar en tiempo real · ▲ ▼ para cambiar de control");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF475569);
        subtitle.setTypeface(activity.getResources().getFont(R.font.outfit_regular));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Math.round(4 * density);
        subLp.bottomMargin = Math.round(14 * density);
        panel.addView(subtitle, subLp);

        final TextView[] valueViews = new TextView[5];
        final View[] rowViews = new View[5];
        final String[] rowLabels = {
                "Modo de Imagen",
                "Intensidad de Iluminación",
                "Detalle en Negros (Luz Sombra)",
                "Temperatura de Color",
                "Matiz de Color (Tinte)"
        };

        Runnable refreshLabels = () -> {
            int preset = getPreset(activity);
            int b = getBrightness(activity);
            int l = getBlackLift(activity);
            int t = getColorTemp(activity);
            int m = getTint(activity);
            valueViews[0].setText("◀  " + PRESET_NAMES[preset] + "  ▶");
            valueViews[1].setText("◀  " + b + "%  ▶");
            valueViews[2].setText("◀  " + l + "% (Realce de grises)  ▶");
            String tempStr = t == 0 ? "Neutro (0)" : (t < 0 ? "Cálido (" + t + ")" : "Frío (+" + t + ")");
            valueViews[3].setText("◀  " + tempStr + "  ▶");
            String tintStr = m == 0 ? "Neutro (0)" : (m < 0 ? "Verde (" + m + ")" : "Magenta (+" + m + ")");
            valueViews[4].setText("◀  " + tintStr + "  ▶");
        };

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int rPadH = Math.round(16 * density);
            int rPadV = Math.round(12 * density);
            row.setPadding(rPadH, rPadV, rPadH, rPadV);
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);
            row.setClickable(true);

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(14 * density);
            rowBg.setColor(0xFFFFFFFF);
            rowBg.setStroke(Math.round(1 * density), 0xFFCBD5E1);
            row.setBackground(rowBg);

            TextView lbl = new TextView(activity);
            lbl.setText(rowLabels[i]);
            lbl.setTextSize(15);
            lbl.setTextColor(0xFF0F172A);
            lbl.setTypeface(activity.getResources().getFont(R.font.outfit_medium));
            row.addView(lbl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView val = new TextView(activity);
            val.setTextSize(15);
            val.setTextColor(0xFF0066CC);
            val.setTypeface(activity.getResources().getFont(R.font.outfit_medium));
            row.addView(val, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            valueViews[i] = val;
            rowViews[i] = row;

            row.setOnFocusChangeListener((v, focused) -> {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(14 * density);
                if (focused) {
                    bg.setColor(0xFF0066CC);
                    bg.setStroke(Math.round(2 * density), 0xFFFFFFFF);
                    lbl.setTextColor(0xFFFFFFFF);
                    val.setTextColor(0xFFFFFFFF);
                } else {
                    bg.setColor(0xFFFFFFFF);
                    bg.setStroke(Math.round(1 * density), 0xFFCBD5E1);
                    lbl.setTextColor(0xFF0F172A);
                    val.setTextColor(0xFF0066CC);
                }
                v.setBackground(bg);
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (idx > 0) rowViews[idx - 1].requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (idx < 4) rowViews[idx + 1].requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    int dir = (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) ? -1 : 1;
                    int b = getBrightness(activity);
                    int l = getBlackLift(activity);
                    int t = getColorTemp(activity);
                    int m = getTint(activity);
                    if (idx == 0) {
                        applyPreset(activity, getPreset(activity) + dir);
                    } else if (idx == 1) {
                        setValues(activity, b + dir * 5, l, t, m);
                    } else if (idx == 2) {
                        setValues(activity, b, l + dir * 5, t, m);
                    } else if (idx == 3) {
                        setValues(activity, b, l, t + dir * 5, m);
                    } else if (idx == 4) {
                        setValues(activity, b, l, t, m + dir * 5);
                    }
                    refreshLabels.run();
                    return true;
                }
                return false;
            });

            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rLp.topMargin = Math.round(8 * density);
            panel.addView(row, rLp);
        }

        refreshLabels.run();

        int width = Math.round(580 * density);
        PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(24 * density);
        if (onDismissed != null) popup.setOnDismissListener(onDismissed::run);
        popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
        panel.post(() -> rowViews[0].requestFocus());
        return popup;
    }
}
