package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.Selection;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.termux.R;

public class TermuxMessageDialogUtils {

    public static void showMessage(Context context, String titleText, String messageText, final DialogInterface.OnDismissListener onDismiss) {
        showMessage(context, titleText, messageText, null, null, null, null, onDismiss);
    }

    public static void showMessage(Context context,
                                   String titleText,
                                   String messageText,
                                   String positiveText,
                                   final DialogInterface.OnClickListener onPositiveButton,
                                   String negativeText,
                                   final DialogInterface.OnClickListener onNegativeButton,
                                   final DialogInterface.OnDismissListener onDismiss
    ) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle(titleText)
            .setMessage(messageText)
            .setPositiveButton(positiveText == null ? context.getString(android.R.string.ok) : positiveText, onPositiveButton);

        if (negativeText != null) {
            builder.setNegativeButton(negativeText, onNegativeButton);
        }

        if (onDismiss != null) {
            builder.setOnDismissListener(onDismiss);
        }

        builder.show();
    }

    public static void exitAppWithErrorMessage(Context context, String titleText, String messageText) {
        showMessage(context, titleText, messageText, dialog -> System.exit(0));
    }

    public static void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    public interface TextSetListener {
        void onTextSet(String text);
    }

    public static void textInput(Activity activity,
                                 int titleText,
                                 int inputHint,
                                 String initialText,
                                 int positiveButtonText, final TextSetListener onPositive,
                                 int neutralButtonText, final TextSetListener onNeutral,
                                 int negativeButtonText, final TextSetListener onNegative,
                                 final DialogInterface.OnDismissListener onDismiss) {
        var input = new EditText(activity);
        input.setSingleLine();
        input.setHint(inputHint);
        if (initialText != null) {
            input.setText(initialText);
            Selection.setSelection(input.getText(), initialText.length());
        }

        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, activity.getResources().getDisplayMetrics());
        // https://www.google.com/design/spec/components/dialogs.html#dialogs-specs
        int paddingTopAndSides = Math.round(16 * dipInPixels);
        int paddingBottom = Math.round(24 * dipInPixels);

        var layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setPadding(paddingTopAndSides, paddingTopAndSides, paddingTopAndSides, paddingBottom);
        layout.addView(input);

        var builder = new AlertDialog.Builder(activity)
            .setTitle(titleText)
            .setView(layout)
            .setPositiveButton(positiveButtonText, (d, whichButton) -> onPositive.onTextSet(input.getText().toString()));

        if (onNeutral != null) {
            builder.setNeutralButton(neutralButtonText, (dialog, which) -> onNeutral.onTextSet(input.getText().toString()));
        }

        if (onNegative == null) {
            builder.setNegativeButton(R.string.cancel, null);
        } else {
            builder.setNegativeButton(negativeButtonText, (dialog, which) -> onNegative.onTextSet(input.getText().toString()));
        }

        if (onDismiss != null) {
            builder.setOnDismissListener(onDismiss);
        }

        var dialog = builder.create();

        input.setImeActionLabel(activity.getResources().getString(positiveButtonText), KeyEvent.KEYCODE_ENTER);
        input.setOnEditorActionListener((v, actionId, event) -> {
            onPositive.onTextSet(input.getText().toString());
            dialog.dismiss();
            return true;
        });

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }



}
