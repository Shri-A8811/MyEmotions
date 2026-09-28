package com.mitaoe.shridhar202401040197;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;
import com.mitaoe.shridhar202401040197.data.AppDatabase;
import com.mitaoe.shridhar202401040197.data.Emotion;
import com.mitaoe.shridhar202401040197.databinding.ActivityLogYourEmotionBinding;
import com.mitaoe.shridhar202401040197.session.SessionManager;
import com.mitaoe.shridhar202401040197.utils.EmotionRecognitionEngine;
import com.mitaoe.shridhar202401040197.utils.SentimentAnalyzer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class LogYourEmotionActivity extends AppCompatActivity {

    private ActivityLogYourEmotionBinding binding;
    private SessionManager sessionManager;

    private String currentPhotoPath = null;
    private String selectedEmotionType = "Happy";
    private EmotionRecognitionEngine.EmotionResult lastFaceResult = null;
    private boolean isUserManualOverride = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    dispatchTakePictureIntent();
                } else {
                    Snackbar.make(binding.getRoot(), "Camera permission is required to capture photos", Snackbar.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && currentPhotoPath != null) {
                    processAndDisplayPhoto(currentPhotoPath);
                }
            });

    private final ActivityResultLauncher<String> pickGalleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    copyGalleryImageToInternalStorage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLogYourEmotionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbarLog);
        binding.toolbarLog.setNavigationOnClickListener(v -> finish());

        binding.btnTakePhoto.setOnClickListener(v -> checkPermissionAndTakePhoto());
        binding.btnGallery.setOnClickListener(v -> pickGalleryLauncher.launch("image/*"));
        binding.btnSaveEmotion.setOnClickListener(v -> saveEmotion());

        setupEmotionChips();
        setupNoteTextWatcher();
    }

    private void setupEmotionChips() {
        binding.chipGroupEmotions.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);

            if (checkedId == R.id.chipHappy) {
                selectedEmotionType = "Happy";
            } else if (checkedId == R.id.chipExcited) {
                selectedEmotionType = "Excited";
            } else if (checkedId == R.id.chipSad) {
                selectedEmotionType = "Sad";
            } else if (checkedId == R.id.chipNeutral) {
                selectedEmotionType = "Neutral";
            } else if (checkedId == R.id.chipSurprised) {
                selectedEmotionType = "Surprised";
            } else if (checkedId == R.id.chipAngry) {
                selectedEmotionType = "Angry";
            }
        });

        // Set click listener on chips to track manual override
        View.OnClickListener chipClickListener = v -> isUserManualOverride = true;
        binding.chipHappy.setOnClickListener(chipClickListener);
        binding.chipExcited.setOnClickListener(chipClickListener);
        binding.chipSad.setOnClickListener(chipClickListener);
        binding.chipNeutral.setOnClickListener(chipClickListener);
        binding.chipSurprised.setOnClickListener(chipClickListener);
        binding.chipAngry.setOnClickListener(chipClickListener);
    }

    private void setupNoteTextWatcher() {
        binding.etNote.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() > 2) {
                    evaluateMultiModalEmotion(s.toString());
                }
            }
        });
    }

    private void evaluateMultiModalEmotion(String noteText) {
        SentimentAnalyzer.SentimentResult noteResult = SentimentAnalyzer.analyzeText(noteText);
        EmotionRecognitionEngine.EmotionResult fused = EmotionRecognitionEngine.combineWithNote(lastFaceResult, noteResult);

        if (!isUserManualOverride) {
            applyEmotionToUi(fused);
        } else {
            // Update rationale without overriding user's manual selection
            binding.tvEmotionRationale.setText(fused.rationale);
        }
    }

    private void checkPermissionAndTakePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void dispatchTakePictureIntent() {
        try {
            File photoFile = createImageFile();
            currentPhotoPath = photoFile.getAbsolutePath();
            Uri currentPhotoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile
            );
            takePictureLauncher.launch(currentPhotoUri);
        } catch (IOException ex) {
            Snackbar.make(binding.getRoot(), "Error creating private image file", Snackbar.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "EMOTION_" + timeStamp + "_";
        File storageDir = new File(getFilesDir(), "emotions");
        if (!storageDir.exists()) {
            boolean created = storageDir.mkdirs();
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void copyGalleryImageToInternalStorage(Uri uri) {
        try {
            File photoFile = createImageFile();
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(photoFile)) {
                if (in != null) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
            }
            currentPhotoPath = photoFile.getAbsolutePath();
            processAndDisplayPhoto(currentPhotoPath);
        } catch (IOException e) {
            Snackbar.make(binding.getRoot(), "Failed to load image from gallery", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void processAndDisplayPhoto(String path) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // Downsample for memory safety
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            if (bitmap != null) {
                bitmap = rotateImageIfRequired(bitmap, path);
                binding.ivPhotoPreview.setImageBitmap(bitmap);
                binding.layoutPhotoPlaceholder.setVisibility(View.GONE);
                binding.btnTakePhoto.setText(getString(R.string.btn_retake_photo));

                isUserManualOverride = false;
                runEmotionRecognition(bitmap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runEmotionRecognition(Bitmap bitmap) {
        binding.tvEmotionStatus.setText(getString(R.string.emotion_analyzing));
        binding.tvEmotionRationale.setText("Processing facial landmarks, curvature, and expression cues…");

        EmotionRecognitionEngine.analyzeFaceEmotion(bitmap, new EmotionRecognitionEngine.EmotionCallback() {
            @Override
            public void onEmotionDetected(EmotionRecognitionEngine.EmotionResult result) {
                runOnUiThread(() -> {
                    lastFaceResult = result;
                    String currentNote = binding.etNote.getText() != null ? binding.etNote.getText().toString() : "";
                    SentimentAnalyzer.SentimentResult noteResult = SentimentAnalyzer.analyzeText(currentNote);
                    EmotionRecognitionEngine.EmotionResult fused = EmotionRecognitionEngine.combineWithNote(result, noteResult);
                    applyEmotionToUi(fused);
                });
            }

            @Override
            public void onNoFaceDetected() {
                runOnUiThread(() -> {
                    lastFaceResult = null;
                    String currentNote = binding.etNote.getText() != null ? binding.etNote.getText().toString() : "";
                    SentimentAnalyzer.SentimentResult noteResult = SentimentAnalyzer.analyzeText(currentNote);

                    if (noteResult != null) {
                        EmotionRecognitionEngine.EmotionResult fused = EmotionRecognitionEngine.combineWithNote(null, noteResult);
                        applyEmotionToUi(fused);
                    } else {
                        binding.tvEmotionStatus.setText(getString(R.string.emotion_no_face));
                        binding.tvEmotionRationale.setText("No clear face detected in photo. Select your emotion or describe it in your note!");
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    binding.tvEmotionStatus.setText(getString(R.string.select_emotion_title));
                    binding.tvEmotionRationale.setText("Choose the mood that best reflects your feelings");
                });
            }
        });
    }

    private void applyEmotionToUi(EmotionRecognitionEngine.EmotionResult result) {
        selectedEmotionType = result.emotion;
        binding.tvEmotionStatus.setText(String.format(getString(R.string.emotion_detected_fmt), result.getFormatted(), result.confidence));
        binding.tvEmotionRationale.setText(result.rationale);

        switch (result.emotion) {
            case "Happy":
                binding.chipHappy.setChecked(true);
                break;
            case "Excited":
                binding.chipExcited.setChecked(true);
                break;
            case "Sad":
                binding.chipSad.setChecked(true);
                break;
            case "Surprised":
                binding.chipSurprised.setChecked(true);
                break;
            case "Angry":
                binding.chipAngry.setChecked(true);
                break;
            case "Neutral":
            default:
                binding.chipNeutral.setChecked(true);
                break;
        }
    }

    private Bitmap rotateImageIfRequired(Bitmap img, String path) {
        try {
            ExifInterface ei = new ExifInterface(path);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return rotateImage(img, 90);
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return rotateImage(img, 180);
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return rotateImage(img, 270);
                default:
                    return img;
            }
        } catch (IOException e) {
            return img;
        }
    }

    private Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private void saveEmotion() {
        if (currentPhotoPath == null) {
            Snackbar.make(binding.getRoot(), getString(R.string.err_no_photo), Snackbar.LENGTH_SHORT).show();
            return;
        }

        String note = binding.etNote.getText() != null ? binding.etNote.getText().toString().trim() : "";
        if (note.isEmpty()) {
            Snackbar.make(binding.getRoot(), getString(R.string.err_no_note), Snackbar.LENGTH_SHORT).show();
            return;
        }

        long userId = sessionManager.getUserId();
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String timeStr = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

        Emotion emotion = new Emotion(userId, currentPhotoPath, note, dateStr, timeStr, selectedEmotionType);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(getApplicationContext()).emotionDao().insertEmotion(emotion);

            runOnUiThread(() -> {
                Toast.makeText(LogYourEmotionActivity.this, getString(R.string.msg_emotion_saved), Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
