package com.afollestad.cabinetsftp.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class FileChooserDialog extends DialogFragment implements MaterialDialog.ListCallback {

    private transient File[] mContents;
    private transient Callback mCallback;
    private transient File mCurrentFolder;

    public static void show(@NonNull Activity context, @NonNull File initialFolder) {
        FileChooserDialog dialog = new FileChooserDialog();
        Bundle args = new Bundle();
        args.putSerializable("current", initialFolder);
        dialog.setArguments(args);
        dialog.show(context.getFragmentManager(), "FILE_CHOOSER");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mCurrentFolder = (File) getArguments().getSerializable("current");
        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .title(mCurrentFolder.getAbsolutePath())
                .autoDismiss(false)
                .items("..")
                .itemsCallback(this)
                .build();
        if (getArguments().containsKey("contents")) {
            mContents = (File[]) getArguments().getSerializable("contents");
            displayResults(dialog);
        } else {
            load(dialog, mCurrentFolder);
        }
        return dialog;
    }

    private void displayResults(MaterialDialog dialog) {
        dialog.setTitle(mCurrentFolder.getAbsolutePath());
        final List<CharSequence> items = new ArrayList<>();
        if (mContents != null && mContents.length > 0) {
            if (mContents[0].getParentFile() != null &&
                    !mContents[0].getParentFile().getAbsolutePath().equals(File.separator)) {
                items.add("..");
            }
            for (File fi : mContents)
                items.add((fi.isDirectory() ? File.separator : "") + fi.getName());
        } else {
            items.add("..");
        }
        dialog.setItems(items.toArray(new CharSequence[items.size()]));
    }

    private void load(MaterialDialog dialog, File from) {
        if (mCurrentFolder.getAbsolutePath().equals("/storage/emulated")) {
            if (from.getAbsolutePath().equals("/storage"))
                mCurrentFolder = Environment.getExternalStorageDirectory();
            else
                mCurrentFolder = new File("/storage");
        }
        mContents = mCurrentFolder.listFiles();
        if (mContents == null) {
            mCurrentFolder = mCurrentFolder.getParentFile();
            if (mCurrentFolder == null) {
                displayResults(dialog);
                return;
            }
            load(dialog, from);
            return;
        }
        getArguments().putSerializable("contents", mContents);
        displayResults(dialog);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallback = (Callback) activity;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        mCallback = null;
    }

    @Override
    public void onSelection(MaterialDialog dialog, View view, int i, CharSequence charSequence) {
        File file;
        if (charSequence.toString().equals("..")) {
            file = mCurrentFolder.getParentFile();
        } else {
            file = mContents[i - 1];
        }
        if (file.isDirectory()) {
            File from = mCurrentFolder;
            setCurrent(file);
            load(dialog, from);
        } else {
            dialog.dismiss();
            mCallback.onChoice(file);
        }
    }

    private void setCurrent(File file) {
        mCurrentFolder = file;
        getArguments().putSerializable("current", file);
    }

    public interface Callback {
        void onChoice(File file);
    }
}
