package me.myshows.android.ui.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

import me.myshows.android.R;

/**
 * Created by Whiplash on 06.09.2015.
 */
public class SignOutDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.sign_out_dialog_title)
                .setMessage(R.string.sign_out_dialog_content)
                .setNegativeButton(R.string.sign_out_dialog_reject, (dialogInterface, i) ->
                        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_CANCELED, null))
                .setPositiveButton(R.string.sign_out_dialog_accept, (dialogInterface1, i) ->
                        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, null))
                .create();
    }
}
