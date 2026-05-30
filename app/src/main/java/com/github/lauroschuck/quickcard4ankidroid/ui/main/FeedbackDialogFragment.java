package com.github.lauroschuck.quickcard4ankidroid.ui.main;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import com.github.lauroschuck.quickcard4ankidroid.R;
import com.github.lauroschuck.quickcard4ankidroid.firebase.FirebaseHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import timber.log.Timber;

public class FeedbackDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "FeedbackDialogFragment";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_feedback, null);
        EditText nameInput = view.findViewById(R.id.feedbackName);
        EditText emailInput = view.findViewById(R.id.feedbackEmail);
        EditText messageInput = view.findViewById(R.id.feedbackMessage);
        TextView errorText = view.findViewById(R.id.feedbackErrorText);
        ProgressBar progressBar = view.findViewById(R.id.feedbackProgressBar);
        View formContainer = view.findViewById(R.id.feedbackFormContainer);

        TextInputLayout nameLayout = (TextInputLayout) nameInput.getParent().getParent();
        TextInputLayout emailLayout = (TextInputLayout) emailInput.getParent().getParent();
        TextInputLayout messageLayout =
                (TextInputLayout) messageInput.getParent().getParent();

        emailInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String email = emailInput.getText().toString().trim();
                if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailLayout.setError(getString(R.string.feedback_error_email_invalid));
                }
            } else {
                emailLayout.setError(null);
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.feedback_title)
                .setView(view)
                .setPositiveButton(R.string.feedback_send, null)
                .setNegativeButton(R.string.common_cancel, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            positiveButton.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                String email = emailInput.getText().toString().trim();
                String message = messageInput.getText().toString().trim();

                boolean isValid = true;

                if (name.isEmpty()) {
                    nameLayout.setError(getString(R.string.feedback_error_name_required));
                    isValid = false;
                } else {
                    nameLayout.setError(null);
                }

                if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailLayout.setError(getString(R.string.feedback_error_email_invalid));
                    isValid = false;
                } else {
                    emailLayout.setError(null);
                }

                if (message.isEmpty()) {
                    messageLayout.setError(getString(R.string.feedback_error_message_empty));
                    isValid = false;
                } else {
                    messageLayout.setError(null);
                }

                if (!isValid) {
                    return;
                }

                // UI feedback: Loading state
                hideKeyboard();
                positiveButton.setEnabled(false);
                negativeButton.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                errorText.setVisibility(View.GONE);
                formContainer.setAlpha(0.5f);

                FirebaseHelper.sendFeedback(
                        name,
                        email,
                        message,
                        documentReference -> {
                            if (isAdded()) {
                                FirebaseHelper.logFeedback(true);
                                if (requireActivity() instanceof MainActivity activity) {
                                    activity.showResultDialog(
                                            R.string.feedback_title, R.string.feedback_success, false);
                                }
                                dismiss();
                            }
                        },
                        e -> {
                            Timber.e(e, "Error sending feedback");
                            if (isAdded()) {
                                FirebaseHelper.logFeedback(false);
                                progressBar.setVisibility(View.GONE);
                                positiveButton.setEnabled(true);
                                negativeButton.setEnabled(true);
                                formContainer.setAlpha(1.0f);
                                errorText.setText(getString(R.string.feedback_error_generic));
                                errorText.setVisibility(View.VISIBLE);
                            }
                        });
            });
        });

        return dialog;
    }

    private void hideKeyboard() {
        View view = requireDialog().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm =
                    (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
