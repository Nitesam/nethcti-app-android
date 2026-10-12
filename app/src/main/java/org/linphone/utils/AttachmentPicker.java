package org.linphone.utils;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import java.io.File;
import java.io.IOException;
import org.linphone.R;

/** Grants access only to the selected document, or a photo explicitly taken by the user. */
public final class AttachmentPicker {
    public interface Callback { void onSelected(Uri uri); }
    private final Fragment fragment;
    private final ActivityResultLauncher<String[]> document;
    private final ActivityResultLauncher<Uri> camera;
    private final ActivityResultLauncher<String> cameraPermission;
    private Uri pendingPhoto;

    public AttachmentPicker(Fragment fragment, Callback callback) {
        this.fragment = fragment;
        document = fragment.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) callback.onSelected(uri);
        });
        camera = fragment.registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            Uri uri = pendingPhoto;
            pendingPhoto = null;
            if (success && uri != null) callback.onSelected(uri);
        });
        cameraPermission = fragment.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) capture();
        });
        fragment.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onCreate(LifecycleOwner owner) {
                Bundle restored = fragment.getSavedStateRegistry().consumeRestoredStateForKey("attachment_picker");
                if (restored != null && restored.getString("photo") != null) {
                    pendingPhoto = Uri.parse(restored.getString("photo"));
                }
                fragment.getSavedStateRegistry().registerSavedStateProvider("attachment_picker", () -> {
                    Bundle state = new Bundle();
                    if (pendingPhoto != null) state.putString("photo", pendingPhoto.toString());
                    return state;
                });
            }
        });
    }

    public void show(String mimeType) {
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.image_picker_title)
                .setItems(new CharSequence[] {
                        fragment.getString(R.string.choose_attachment),
                        fragment.getString(R.string.take_attachment_photo)}, (dialog, which) -> {
                    if (which == 0) {
                        document.launch(new String[] {mimeType});
                    } else if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        capture();
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA);
                    }
                }).show();
    }

    private void capture() {
        try {
            File directory = new File(fragment.requireContext().getCacheDir(), "attachments");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No capture directory");
            File file = File.createTempFile("photo-", ".jpg", directory);
            pendingPhoto = FileProvider.getUriForFile(fragment.requireContext(),
                    fragment.getString(R.string.file_provider), file);
            camera.launch(pendingPhoto);
        } catch (IOException | android.content.ActivityNotFoundException e) {
            pendingPhoto = null;
            Toast.makeText(fragment.requireContext(), R.string.attachment_open_failed, Toast.LENGTH_LONG).show();
        }
    }
}
