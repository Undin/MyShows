package me.myshows.android.ui.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import me.myshows.android.R;

/**
 * Created by Whiplash on 06.09.2015.
 */
public class ClearCacheDialogFragment extends BaseDialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.clear_cache_dialog_title)
                .setMessage(R.string.clear_cache_dialog_content)
                .setNegativeButton(R.string.clear_cache_dialog_reject, (dialogInterface, i) ->
                        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_CANCELED, null))
                .setPositiveButton(R.string.clear_cache_dialog_accept, (dialogInterface, i) ->
                        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, null))
                .create();
    }
}
