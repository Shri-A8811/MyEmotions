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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogYourEmotionActivity extends AppCompatActivity {

    private ActivityLogYourEmotionBinding binding;
    private SessionManager sessionManager;

    private String currentPhotoPath = null;
    private String selectedEmotionType = "Happy";
    private EmotionRecognitionEngine.EmotionResult lastFaceResult = null;
    private boolean isUserManualOverride = false;

    // CameraX Live Detection Fields
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private ExecutorService cameraExecutor;
    private boolean isLiveCameraActive = false;
    private final AtomicBoolean isAnalyzing = new AtomicBoolean(false);
    private boolean pendingLiveCamera = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (pendingLiveCamera) {
                        startLiveCamera();
                    } else {
                        dispatchTakePictureIntent();
                    }
                } else {
                    Snackbar.make(binding.getRoot(), "Camera permission is required to use camera features", Snackbar.LENGTH_SHORT).show();
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
        cameraExecutor = Executors.newSingleThreadExecutor();

        setSupportActionBar(binding.toolbarLog);
        binding.toolbarLog.setNavigationOnClickListener(v -> finish());

        binding.btnLiveCamera.setOnClickListener(v -> toggleLiveCamera());
        binding.btnSwitchCamera.setOnClickListener(v -> switchCameraLens());
        binding.btnCaptureLive.setOnClickListener(v -> captureLivePhoto());
        binding.layoutPhotoPlaceholder.setOnClickListener(v -> toggleLiveCamera());

        binding.btnTakePhoto.setOnClickListener(v -> checkPermissionAndTakePhoto());
        binding.btnGallery.setOnClickListener(v -> {
            if (isLiveCameraActive) {
                stopLiveCamera();
            }
            pickGalleryLauncher.launch("image/*");
        });
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
        pendingLiveCamera = false;
        if (isLiveCameraActive) {
            stopLiveCamera();
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void toggleLiveCamera() {
        if (isLiveCameraActive) {
            stopLiveCamera();
        } else {
            pendingLiveCamera = true;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startLiveCamera();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        }
    }

    private void startLiveCamera() {
        isLiveCameraActive = true;
        binding.cameraPreviewView.setVisibility(View.VISIBLE);
        binding.layoutLiveHud.setVisibility(View.VISIBLE);
        binding.btnSwitchCamera.setVisibility(View.VISIBLE);
        binding.btnCaptureLive.setVisibility(View.VISIBLE);
        binding.ivPhotoPreview.setVisibility(View.GONE);
        binding.layoutPhotoPlaceholder.setVisibility(View.GONE);
        binding.btnLiveCamera.setText("🛑 Stop Live");
        binding.tvLiveEmotionBadge.setText(R.string.live_detecting);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                if (!cameraProvider.hasCamera(cameraSelector)) {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                }
                bindCameraUseCases();
            } catch (Exception e) {
                Snackbar.make(binding.getRoot(), "Unable to initialize camera preview: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                stopLiveCamera();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.cameraPreviewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (!isLiveCameraActive || isAnalyzing.get()) {
                imageProxy.close();
                return;
            }

            isAnalyzing.set(true);
            try {
                Bitmap rawBitmap = imageProxy.toBitmap();
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                imageProxy.close();

                if (rawBitmap != null) {
                    Bitmap uprightBitmap;
                    if (rotationDegrees != 0) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate(rotationDegrees);
                        uprightBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.getWidth(), rawBitmap.getHeight(), matrix, true);
                        if (uprightBitmap != rawBitmap) {
                            rawBitmap.recycle();
                        }
                    } else {
                        uprightBitmap = rawBitmap;
                    }

                    Bitmap analysisBitmap;
                    if (uprightBitmap.getWidth() > 360) {
                        int targetHeight = Math.max(1, (int) (360.0f * uprightBitmap.getHeight() / uprightBitmap.getWidth()));
                        analysisBitmap = Bitmap.createScaledBitmap(uprightBitmap, 360, targetHeight, true);
                        if (analysisBitmap != uprightBitmap) {
                            uprightBitmap.recycle();
                        }
                    } else {
                        analysisBitmap = uprightBitmap;
                    }

                    EmotionRecognitionEngine.analyzeFaceEmotion(analysisBitmap, new EmotionRecognitionEngine.EmotionCallback() {
                        @Override
                        public void onEmotionDetected(EmotionRecognitionEngine.EmotionResult result) {
                            try {
                                analysisBitmap.recycle();
                            } catch (Throwable ignored) {}
                            isAnalyzing.set(false);
                            runOnUiThread(() -> {
                                if (!isLiveCameraActive) return;
                                lastFaceResult = result;
                                String hudText = String.format(Locale.getDefault(), "LIVE: %s (%d%%)", result.getFormatted(), result.confidence);
                                binding.tvLiveEmotionBadge.setText(hudText);
                                if (!isUserManualOverride) {
                                    applyEmotionToUi(result);
                                } else {
                                    binding.tvEmotionRationale.setText(result.rationale);
                                }
                            });
                        }

                        @Override
                        public void onNoFaceDetected() {
                            try {
                                analysisBitmap.recycle();
                            } catch (Throwable ignored) {}
                            isAnalyzing.set(false);
                            runOnUiThread(() -> {
                                if (!isLiveCameraActive) return;
                                binding.tvLiveEmotionBadge.setText(R.string.live_detecting);
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            try {
                                analysisBitmap.recycle();
                            } catch (Throwable ignored) {}
                            isAnalyzing.set(false);
                        }
                    });
                } else {
                    isAnalyzing.set(false);
                }
            } catch (Throwable t) {
                try {
                    imageProxy.close();
                } catch (Throwable ignored) {}
                isAnalyzing.set(false);
            }
        });

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "Camera binding failed: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void switchCameraLens() {
        CameraSelector targetSelector = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                ? CameraSelector.DEFAULT_BACK_CAMERA
                : CameraSelector.DEFAULT_FRONT_CAMERA;
        try {
            if (cameraProvider != null && cameraProvider.hasCamera(targetSelector)) {
                cameraSelector = targetSelector;
                if (isLiveCameraActive) {
                    bindCameraUseCases();
                }
            } else {
                Snackbar.make(binding.getRoot(), "Camera lens not available on this device", Snackbar.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "Unable to switch camera: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void stopLiveCamera() {
        isLiveCameraActive = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        binding.cameraPreviewView.setVisibility(View.GONE);
        binding.layoutLiveHud.setVisibility(View.GONE);
        binding.btnSwitchCamera.setVisibility(View.GONE);
        binding.btnCaptureLive.setVisibility(View.GONE);
        binding.btnLiveCamera.setText(R.string.btn_live_ai);

        if (currentPhotoPath != null) {
            binding.ivPhotoPreview.setVisibility(View.VISIBLE);
            binding.layoutPhotoPlaceholder.setVisibility(View.GONE);
        } else {
            binding.ivPhotoPreview.setVisibility(View.GONE);
            binding.layoutPhotoPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    private void captureLivePhoto() {
        if (imageCapture == null) return;
        try {
            File photoFile = createImageFile();
            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
            binding.btnCaptureLive.setEnabled(false);

            imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    binding.btnCaptureLive.setEnabled(true);
                    currentPhotoPath = photoFile.getAbsolutePath();
                    stopLiveCamera();
                    processAndDisplayPhoto(currentPhotoPath);
                    binding.etNote.requestFocus();
                    Snackbar.make(binding.getRoot(), "Photo captured! Mood locked: " + (lastFaceResult != null ? lastFaceResult.getFormatted() : selectedEmotionType), Snackbar.LENGTH_SHORT).show();
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    binding.btnCaptureLive.setEnabled(true);
                    Snackbar.make(binding.getRoot(), "Capture failed: " + exception.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            binding.btnCaptureLive.setEnabled(true);
            Snackbar.make(binding.getRoot(), "Failed to prepare file: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
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
        } catch (Exception ex) {
            Snackbar.make(binding.getRoot(), "Unable to launch camera. Please verify device camera availability.", Snackbar.LENGTH_LONG).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "EMOTION_" + timeStamp + "_";
        File storageDir = new File(getFilesDir(), "emotions");
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            storageDir = getFilesDir();
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
        } catch (Throwable e) {
            Snackbar.make(binding.getRoot(), "Failed to load image from gallery", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void processAndDisplayPhoto(String path) {
        try {
            Bitmap bitmap = decodeSampledBitmap(path, 1024, 1024);
            if (bitmap != null) {
                bitmap = rotateImageIfRequired(bitmap, path);
                binding.ivPhotoPreview.setImageBitmap(bitmap);
                binding.layoutPhotoPlaceholder.setVisibility(View.GONE);
                binding.btnTakePhoto.setText(getString(R.string.btn_retake_photo));

                isUserManualOverride = false;
                runEmotionRecognition(bitmap);
            } else {
                Snackbar.make(binding.getRoot(), "Unable to decode photo preview", Snackbar.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Snackbar.make(binding.getRoot(), "Error processing photo", Snackbar.LENGTH_SHORT).show();
        }
    }

    public static Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            int inSampleSize = 1;
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            options.inSampleSize = Math.max(1, inSampleSize);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable t) {
            return null;
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
        } catch (Throwable e) {
            return img;
        }
    }

    private Bitmap rotateImage(Bitmap img, int degree) {
        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
            if (rotatedImg != img) {
                img.recycle();
            }
            return rotatedImg;
        } catch (Throwable t) {
            return img;
        }
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

    @Override
    protected void onPause() {
        super.onPause();
        if (isLiveCameraActive) {
            stopLiveCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isLiveCameraActive) {
            stopLiveCamera();
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
    }
}
