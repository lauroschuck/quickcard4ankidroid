package com.github.lauroschuck.ankiquickadd.ui.main;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.github.lauroschuck.ankiquickadd.R;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import timber.log.Timber;

public class AboutDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "AboutDialogFragment";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null);
        TextView versionText = view.findViewById(R.id.aboutVersion);
        Button homepageButton = view.findViewById(R.id.aboutHomepageButton);
        Button licensesButton = view.findViewById(R.id.aboutLicensesButton);
        Button closeButton = view.findViewById(R.id.aboutCloseButton);

        try {
            PackageInfo pInfo = requireContext()
                    .getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            versionText.setText(getString(R.string.about_version, pInfo.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            Timber.w(e, "Failed to get package info for version display");
            versionText.setVisibility(View.GONE);
        }

        homepageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_homepage_url)));
            startActivity(intent);
        });

        licensesButton.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), OssLicensesMenuActivity.class));
            dismiss();
        });

        closeButton.setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(requireContext()).setView(view).create();
    }
}
