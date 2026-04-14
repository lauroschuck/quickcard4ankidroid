package com.github.lauroschuck.ankiquickadd;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
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

        TextInputLayout nameLayout = (TextInputLayout) nameInput.getParent().getParent();
        TextInputLayout emailLayout = (TextInputLayout) emailInput.getParent().getParent();
        TextInputLayout messageLayout =
                (TextInputLayout) messageInput.getParent().getParent();

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.feedback_title)
                .setView(view)
                .setPositiveButton(R.string.feedback_send, null)
                .setNegativeButton(R.string.feedback_cancel, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(v -> {
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

                button.setEnabled(false);

                FirebaseHelper.sendFeedback(
                        name,
                        email,
                        message,
                        documentReference -> {
                            if (isAdded()) {
                                FirebaseHelper.logFeedback(true);
                                Toast.makeText(requireContext(), R.string.feedback_success, Toast.LENGTH_SHORT)
                                        .show();
                                dismiss();
                            }
                        },
                        e -> {
                            Timber.e(e, "Error sending feedback");
                            if (isAdded()) {
                                FirebaseHelper.logFeedback(false);
                                Toast.makeText(requireContext(), R.string.feedback_error_generic, Toast.LENGTH_SHORT)
                                        .show();
                                button.setEnabled(true);
                            }
                        });
            });
        });

        return dialog;
    }
}
