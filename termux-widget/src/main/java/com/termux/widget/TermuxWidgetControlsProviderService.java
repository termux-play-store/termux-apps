package com.termux.widget;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.controls.Control;
import android.service.controls.ControlsProviderService;
import android.service.controls.DeviceTypes;
import android.service.controls.actions.CommandAction;
import android.service.controls.actions.ControlAction;
import android.service.controls.templates.ControlTemplate;
import android.service.controls.templates.StatelessTemplate;

import androidx.annotation.RequiresApi;

import org.reactivestreams.FlowAdapters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import io.reactivex.Flowable;
import io.reactivex.processors.ReplayProcessor;

/**
 * ControlProviderService for Android 11+ Device Control which allows running
 * Termux Widget shortcuts from device Power Menu.
 * See <a href="https://developer.android.com/guide/topics/ui/device-control">Control external devices</a>
 */
@RequiresApi(api = Build.VERSION_CODES.R)
public class TermuxWidgetControlsProviderService extends ControlsProviderService {

    private static final int WIDGET_REQUEST_CODE = 2233;
    private static final String STATELESS_TEMPLATE_ID = "2";

    private final ReplayProcessor<Control> mReplayProcessor = ReplayProcessor.create();

    /**
     * Creates the initial selection of all our shortcut widget controls that the user can add.
     * Android system will subscribe to our flow to display items to user
     *
     * @return Flow.Publisher<Control>
     */
    @Override
    public Flow.Publisher<Control> createPublisherForAllAvailable() {
        List<File> shortcutFiles = createShortcutFilesList();
        List<Control> controlList = new ArrayList<>();

        for (File shortcutFile : shortcutFiles) {
            Control control = createDefaultSelectableWidgetControl(shortcutFile);
            controlList.add(control);
        }
        return FlowAdapters.toFlowPublisher(Flowable.fromIterable(controlList));
    }

    /**
     * Creates the selection of widget controls that the user has added and adds the necessary
     * PendingIntent to execute the widget action.
     * Android system will subscribe to our flow to display items to user
     *
     * @param list List of widget control ids (shortcut paths) that user has added
     * @return Flow.Publisher<Control>
     */
    @Override
    public Flow.Publisher<Control> createPublisherFor(List<String> list) {
        for (String shortcutFilePath : list) {
            File shortcutFile = new File(shortcutFilePath);
            Control control;

            if (!shortcutFile.isFile() || !shortcutFile.exists()) {
                control = null; // createWidgetControlForInvalidShortcutFile(shortcutFile);
            } else {
                control = createWidgetControlForValidShortcutFile(shortcutFile);
            }
            mReplayProcessor.onNext(control);
        }
        return FlowAdapters.toFlowPublisher(mReplayProcessor);
    }

    /**
     * Notify consumers that action was performed.
     *
     * @param controlId
     * @param controlAction
     * @param consumer
     */
    @Override
    public void performControlAction(String controlId, ControlAction controlAction, Consumer<Integer> consumer) {
        // our controls have no custom UI interaction, they only fire a command so
        // we just need to notify consumer that we have handled successfully
        consumer.accept(ControlAction.RESPONSE_OK);

        if (controlAction instanceof CommandAction) {
            performWidgetCommandAction(controlId);
            File file = new File(controlId);
            if (file.isFile() && file.exists()) {
                // run via BroadcastReceiver instead of Activity so we don't have
                // the BottomSheet briefly appearing
                //Intent intent = createBroadcastIntentForShortcutFile(file);
                //sendBroadcast(intent);
            }
        }
    }

    private void performWidgetCommandAction(String filePath) {
    }

    /**
     * Creates Control Widget that user can add, but is not already added
     *
     * @param shortcutFile
     * @return Control
     */
    private Control createDefaultSelectableWidgetControl(File shortcutFile) {
        PendingIntent emptyPendingIntent = createNoopPendingIntent();

        return new Control.StatelessBuilder(shortcutFile.getAbsolutePath(), emptyPendingIntent)
            .setTitle(shortcutFile.getName())
            .setSubtitle(createSubtitle(shortcutFile))
            .setCustomIcon(createDefaultIcon())
            .setDeviceType(DeviceTypes.TYPE_UNKNOWN)
            .build();
    }

    /**
     * Creates Control widget that will fire off commands from the shortcutFile.
     *
     * @param shortcutFile
     * @return Control
     */
    private Control createWidgetControlForValidShortcutFile(File shortcutFile) {
        PendingIntent pendingIntent = createNoopPendingIntent();

        return new Control.StatefulBuilder(shortcutFile.getAbsolutePath(), pendingIntent)
            .setTitle(shortcutFile.getName())
            .setSubtitle(createSubtitle(shortcutFile))
            .setCustomIcon(createDefaultIcon())
            .setControlTemplate(createDefaultStatelessTemplate())
            .setDeviceType(DeviceTypes.TYPE_UNKNOWN)
            .setStatus(Control.STATUS_OK)
            .build();
    }

    /**
     * Display shortcut file parent name as subtitle
     *
     * @param file
     * @return String
     */
    private String createSubtitle(File file) {
        return file.getParentFile() != null ? file.getParentFile().getName() : "???";
    }

    /**
     * Use our launcher icon as default icon for our Control widgets
     *
     * @return Icon
     */
    private Icon createDefaultIcon() {
        return Icon.createWithResource(getBaseContext(), R.mipmap.ic_launcher);
    }

    private ControlTemplate createDefaultStatelessTemplate() {
        return new StatelessTemplate(STATELESS_TEMPLATE_ID);
    }

    /**
     * Create PendingIntent that does nothing, but is required for Control builder as
     * we cannot pass null there.
     *
     * @return PendingIntent
     */
    private PendingIntent createNoopPendingIntent() {
        Intent emptyIntent = new Intent();
        return PendingIntent.getActivity(getBaseContext(), 1, emptyIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    private List<File> createShortcutFilesList() {
        File shortcutDir = null;

        List<File> shortcutFiles = new ArrayList<>();
        addShortcutFile(shortcutDir, shortcutFiles, 0);

        // sort by file name
        shortcutFiles.sort((lhs, rhs) -> NaturalOrderComparator.compare(lhs.getName(), rhs.getName()));
        return shortcutFiles;
    }

    private void addShortcutFile(File dir, List<File> shortcutFiles, int depth) {
        if (depth > 5) {
            // max depth defined from TermuxWidgetService so using same here
            return;
        }

        File[] files = null; // dir.listFiles(ShortcutUtils.SHORTCUT_FILES_FILTER);
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                addShortcutFile(file, shortcutFiles, depth + 1);
            } else {
                shortcutFiles.add(file);
            }
        }
    }

}
