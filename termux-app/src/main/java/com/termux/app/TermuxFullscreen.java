package com.termux.app;

import android.view.WindowInsets;
import android.view.WindowManager;

import com.termux.R;

/**
 * See <a href="https://developer.android.com/develop/ui/views/layout/insets/rounded-corners">Insets: Apply rounded corners</a>
 * and <a href="https://developer.android.com/develop/ui/views/layout/sw-keyboard">Control and animate the software keyboard</a>.
 */
public class TermuxFullscreen {

    private static final boolean CORNERS_API = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S);

    public static void updatePadding(TermuxActivity activity, WindowInsets insets) {
        var rootView = activity.findViewById(R.id.activity_termux_root_relative_layout);
        if (activity.mPreferences.isFullscreen()) {
            var radiusTopLeft = cornerRadius(insets, 0);
            var radiusTopRight = cornerRadius(insets, 1);
            var radiusBottomRight = cornerRadius(insets, 2);
            var radiusBottomLeft = cornerRadius(insets, 3);

            var windowManager = activity.getSystemService(WindowManager.class);
            var windowBounds = windowManager.getCurrentWindowMetrics().getBounds();
            int[] location = {0, 0};
            rootView.getLocationInWindow(location);

            int topMargin = location[1] - windowBounds.top;
            // Do not go below 0, see https://github.com/termux-play-store/termux-apps/issues/62:
            int bottomMargin = Math.max(0, windowBounds.bottom - rootView.getBottom());

            int imeHeight = insets.getInsets(WindowInsets.Type.ime()).bottom;
            var topPadding = TermuxFullscreen.calculatePadding(radiusTopLeft, radiusTopRight, topMargin);
            var bottomPadding = Math.max(imeHeight, TermuxFullscreen.calculatePadding(radiusBottomLeft, radiusBottomRight, bottomMargin));
            rootView.setPadding(0, topPadding, 0, bottomPadding);
        } else {
            rootView.setPadding(0, 0, 0, 0);
        }
    }

    private static int cornerRadius(WindowInsets insets, int position) {
        if (CORNERS_API) {
            var corner = insets.getRoundedCorner(position);
            return corner == null ? 0 : corner.getRadius();
        } else {
            return 0;
        }
    }

    private static int calculatePadding(int radius1, int radius2, int margin) {
        return Math.max(Math.max(radius1, radius2) - margin, 0);
    }

}
