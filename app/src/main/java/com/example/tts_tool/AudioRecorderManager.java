package com.example.tts_tool;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Manages high-quality audio recording using AudioRecord, suitable for TTS model training.
 * Records uncompressed WAV files with specified parameters, including silence padding
 * and AGC bypass attempts.
 */
public class AudioRecorderManager {

    private static final String TAG = "AudioRecorderManager";

    // --- Configurable Audio Recording Parameters ---
    // Default sampling rate in Hz. Can be 16000, 22050, or 24000.
    private static final int DEFAULT_SAMPLE_RATE = 22050;

    // Default audio encoding. Use AudioFormat.ENCODING_PCM_16BIT for 16-bit PCM.
    // For 24-bit equivalent, you would use AudioFormat.ENCODING_PCM_FLOAT, which
    // stores audio as 32-bit floating point numbers.
    private static final int DEFAULT_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    // Channel configuration. Use AudioFormat.CHANNEL_IN_MONO for mono audio.
    private static final int DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    // Duration of silence padding to add at the beginning and end of recordings (in milliseconds).
    private static final int SILENCE_DURATION_MS = 150;

    // --- Internal State Variables ---
    private AudioRecord audioRecord = null;
    private boolean isRecording = false;
    private ByteArrayOutputStream audioDataBuffer; // Buffer to hold raw audio data in memory
    private Context context;
    private DocumentFile outputFile; // The DocumentFile where the final WAV will be saved
    private ExecutorService executorService; // For background recording and file writing
    private Future<?> recordingTask; // Reference to the submitted recording task

    /**
     * Callback interface to notify the UI or calling component about recording status.
     */
    public interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingStopped(Uri fileUri);
        void onRecordingError(String errorMessage);
    }

    private RecordingCallback callback;

    /**
     * Constructor for AudioRecorderManager.
     * @param context The application context.
     * @param callback An implementation of RecordingCallback to receive status updates.
     */
    public AudioRecorderManager(Context context, RecordingCallback callback) {
        this.context = context;
        this.callback = callback;
        // Initialize a single-threaded executor for sequential recording and file writing tasks.
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Initializes and starts audio recording.
     * This method should be called from the main thread, but the actual recording
     * will happen on a background thread.
     *
     * @param outputDocumentFile The DocumentFile where the WAV audio will be saved.
     * This should be obtained, for example, via Storage Access Framework
     * (e.g., using ACTION_CREATE_DOCUMENT).
     */
    public void startRecording(DocumentFile outputDocumentFile) {
        if (isRecording) {
            Log.w(TAG, "Recording is already in progress.");
            return;
        }

        if (outputDocumentFile == null) {
            Log.e(TAG, "Output DocumentFile is null. Cannot start recording.");
            if (callback != null) {
                // Post to main looper for UI updates
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onRecordingError("Output file not specified."));
            }
            return;
        }

        this.outputFile = outputDocumentFile;
        // Initialize a new ByteArrayOutputStream for each recording
        audioDataBuffer = new ByteArrayOutputStream();

        // Determine the audio source to attempt AGC bypass.
        // UNPROCESSED is ideal for raw audio without processing (API 24+).
        // VOICE_RECOGNITION is a good fallback for less processing (API 16+).
        int audioSource = MediaRecorder.AudioSource.MIC; // Default fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24+
            audioSource = MediaRecorder.AudioSource.UNPROCESSED;
            Log.d(TAG, "Attempting AudioSource.UNPROCESSED for AGC bypass.");
        } else { // API 16+
            audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
            Log.d(TAG, "Using AudioSource.VOICE_RECOGNITION as UNPROCESSED is not available.");
        }

        // Get the minimum buffer size required for the AudioRecord instance.
        // This is crucial for efficient audio capture.
        int minBufferSize = AudioRecord.getMinBufferSize(
                DEFAULT_SAMPLE_RATE,
                DEFAULT_CHANNEL_CONFIG,
                DEFAULT_AUDIO_ENCODING
        );

        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "AudioRecord.getMinBufferSize returned an error: " + minBufferSize);
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onRecordingError("Failed to get minimum buffer size for audio recording."));
            }
            return;
        }

        // Use a buffer size that is a multiple of the minimum buffer size for better performance.
        // A larger buffer can reduce the chance of audio dropouts but increases latency.
        int bufferSize = minBufferSize * 2;

        // Check for RECORD_AUDIO permission. This should ideally be handled by the calling Activity/Fragment.
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted. Cannot start recording.");
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onRecordingError("RECORD_AUDIO permission not granted. Please grant permission."));
            }
            return;
        }

        try {
            // Initialize AudioRecord with the chosen parameters
            audioRecord = new AudioRecord(
                    audioSource,
                    DEFAULT_SAMPLE_RATE,
                    DEFAULT_CHANNEL_CONFIG,
                    DEFAULT_AUDIO_ENCODING,
                    bufferSize
            );

            // Check if AudioRecord initialized successfully
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed. State: " + audioRecord.getState());
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onRecordingError("AudioRecord initialization failed."));
                }
                releaseAudioRecord(); // Clean up
                return;
            }

            isRecording = true;
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onRecordingStarted());
            }

            // Submit the recording task to the executor service.
            // This runs the actual audio capture on a background thread.
            recordingTask = executorService.submit(() -> {
                try {
                    audioRecord.startRecording();
                    Log.d(TAG, "AudioRecord started recording.");

                    // Add silence padding at the beginning of the recording
                    addSilencePadding(audioDataBuffer, SILENCE_DURATION_MS, DEFAULT_SAMPLE_RATE, DEFAULT_AUDIO_ENCODING);

                    // Buffer to read audio data into. Size matches the AudioRecord buffer.
                    byte[] audioBuffer = new byte[bufferSize];

                    // Main recording loop
                    while (isRecording) {
                        int bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                        if (bytesRead > 0) {
                            // Write read bytes to the in-memory buffer
                            audioDataBuffer.write(audioBuffer, 0, bytesRead);
                        } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "AudioRecord.read: Invalid operation. Stopping recording.");
                            isRecording = false; // Stop recording on error
                            new Handler(Looper.getMainLooper()).post(() ->
                                    callback.onRecordingError("AudioRecord read error: Invalid operation."));
                        } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord.read: Bad value. Stopping recording.");
                            isRecording = false; // Stop recording on error
                            new Handler(Looper.getMainLooper()).post(() ->
                                    callback.onRecordingError("AudioRecord read error: Bad value."));
                        }
                        // Handle other AudioRecord.ERROR codes if necessary
                    }

                    // Once recording stops, add silence padding at the end
                    addSilencePadding(audioDataBuffer, SILENCE_DURATION_MS, DEFAULT_SAMPLE_RATE, DEFAULT_AUDIO_ENCODING);

                    Log.d(TAG, "Recording loop finished. Total raw audio bytes buffered: " + audioDataBuffer.size());

                } catch (Exception e) {
                    Log.e(TAG, "Error during recording process: " + e.getMessage(), e);
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                callback.onRecordingError("Error during recording: " + e.getMessage()));
                    }
                } finally {
                    // Ensure AudioRecord is stopped and released even if an error occurs
                    stopAndReleaseAudioRecord();
                }
            });

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error creating AudioRecord instance: " + e.getMessage(), e);
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onRecordingError("Error initializing AudioRecord: " + e.getMessage()));
            }
            releaseAudioRecord(); // Clean up
        }
    }

    /**
     * Stops audio recording. This method signals the background recording thread to stop,
     * waits for it to finish, and then writes the buffered audio data to the WAV file.
     * This should be called from the main thread.
     */
    public void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording. No action needed.");
            return;
        }

        isRecording = false; // Signal the recording thread to stop its loop
        Log.d(TAG, "Stop signal sent to recording thread.");

        // Wait for the recording task to complete its current read cycle and add final silence.
        // This ensures all audio and padding are captured before proceeding to file writing.
        if (recordingTask != null) {
            try {
                // .get() blocks until the task completes (or throws an exception)
                recordingTask.get();
                Log.d(TAG, "Recording task successfully completed.");
            } catch (Exception e) {
                Log.e(TAG, "Error waiting for recording task to complete: " + e.getMessage(), e);
                // If an error occurred in the recording task, report it
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onRecordingError("Error finalizing recording: " + e.getMessage()));
                }
            }
        }

        // Now, write the WAV file to the DocumentFile on the background thread
        executorService.submit(() -> {
            OutputStream outputStream = null;
            try {
                if (outputFile == null) {
                    throw new IOException("Output DocumentFile is null. Cannot save WAV.");
                }

                // Open the output stream for the DocumentFile.
                // This is how you write to a user-selected file location via SAF.
                outputStream = context.getContentResolver().openOutputStream(outputFile.getUri());
                if (outputStream == null) {
                    throw new IOException("Failed to open output stream for DocumentFile: " + outputFile.getUri());
                }

                byte[] pcmData = audioDataBuffer.toByteArray(); // Get all buffered raw PCM data
                long totalAudioLen = pcmData.length; // Length of raw audio data in bytes
                long longSampleRate = DEFAULT_SAMPLE_RATE;
                // Determine number of channels based on configuration
                int channels = (DEFAULT_CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) ? 1 : 2;
                // Calculate byte rate (bytes per second)
                long byteRate = (longSampleRate * channels * (getBitsPerSample(DEFAULT_AUDIO_ENCODING) / 8));

                // Write the WAV header first
                writeWavHeader(outputStream, totalAudioLen, totalAudioLen + 36,
                        longSampleRate, channels, byteRate, DEFAULT_AUDIO_ENCODING);
                // Then write the actual PCM audio data
                outputStream.write(pcmData);

                Log.d(TAG, "WAV file written successfully to: " + outputFile.getUri());
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onRecordingStopped(outputFile.getUri()));
                }

            } catch (IOException e) {
                Log.e(TAG, "Error writing WAV file: " + e.getMessage(), e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onRecordingError("Error saving WAV file: " + e.getMessage()));
                }
            } finally {
                // Always close the output stream to ensure data is flushed and resources are released
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    audioDataBuffer.reset(); // Clear the buffer for the next recording
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream: " + e.getMessage(), e);
                }
                // AudioRecord should already be released by stopAndReleaseAudioRecord in the recording thread's finally block
            }
        });
    }

    /**
     * Releases the AudioRecord instance. This method is primarily for cleanup
     * if recording hasn't started or if there's an early error.
     */
    private void releaseAudioRecord() {
        if (audioRecord != null) {
            // Stop recording if it's active before releasing
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    audioRecord.stop();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error stopping AudioRecord during early release: " + e.getMessage());
                }
            }
            audioRecord.release();
            audioRecord = null;
            Log.d(TAG, "AudioRecord instance released.");
        }
    }

    /**
     * Stops and releases the AudioRecord instance. This method is intended to be called
     * from the recording thread's finally block to ensure proper cleanup after recording ends
     * or an error occurs during recording.
     */
    private void stopAndReleaseAudioRecord() {
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    audioRecord.stop();
                    Log.d(TAG, "AudioRecord stopped successfully.");
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error stopping AudioRecord in finally block: " + e.getMessage());
                }
            }
            audioRecord.release();
            audioRecord = null;
            Log.d(TAG, "AudioRecord released in finally block.");
        }
    }

    /**
     * Adds silence padding (zero-filled bytes) to the audio data buffer.
     * The silence is generated based on the specified duration, sample rate, and audio encoding.
     *
     * @param buffer The ByteArrayOutputStream to write silence to.
     * @param durationMs The duration of silence in milliseconds.
     * @param sampleRate The sample rate of the audio.
     * @param audioEncoding The audio encoding (e.g., AudioFormat.ENCODING_PCM_16BIT).
     */
    private void addSilencePadding(ByteArrayOutputStream buffer, int durationMs, int sampleRate, int audioEncoding) {
        int bytesPerSample = getBitsPerSample(audioEncoding) / 8;
        // Calculate the number of samples needed for the specified silence duration
        int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        // Create a byte array filled with zeros (default for new byte arrays)
        byte[] silenceBytes = new byte[numSamples * bytesPerSample];
        try {
            buffer.write(silenceBytes);
            Log.d(TAG, "Added " + numSamples + " samples (" + silenceBytes.length + " bytes) of silence padding.");
        } catch (IOException e) {
            Log.e(TAG, "Error adding silence padding: " + e.getMessage());
        }
    }

    /**
     * Writes the WAV file header to the provided output stream.
     * This method constructs the standard RIFF WAV header.
     * Reference for WAV format: http://soundfile.sapp.org/doc/WaveFormat/
     *
     * @param out The output stream to write the header to.
     * @param totalAudioLen Total length of the audio data (PCM data) in bytes.
     * @param totalDataLen Total length of the file (totalAudioLen + 36 bytes for header fields after RIFF).
     * @param longSampleRate Sample rate in Hz.
     * @param channels Number of audio channels (1 for mono, 2 for stereo).
     * @param byteRate Byte rate (sampleRate * channels * bytesPerSample).
     * @param audioEncoding Audio encoding (e.g., AudioFormat.ENCODING_PCM_16BIT, ENCODING_PCM_FLOAT).
     * @throws IOException If an I/O error occurs during writing.
     */
    private void writeWavHeader(OutputStream out, long totalAudioLen, long totalDataLen,
                                long longSampleRate, int channels, long byteRate, int audioEncoding) throws IOException {

        int bitsPerSample = getBitsPerSample(audioEncoding);
        int audioFormat; // 1 for PCM, 3 for IEEE Float
        if (audioEncoding == AudioFormat.ENCODING_PCM_16BIT || audioEncoding == AudioFormat.ENCODING_PCM_8BIT) {
            audioFormat = 1; // PCM
        } else if (audioEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            audioFormat = 3; // IEEE Float
        } else {
            audioFormat = 1; // Default to PCM if unknown or unsupported
            Log.w(TAG, "Unsupported audio encoding for WAV header: " + audioEncoding + ". Defaulting to PCM format.");
        }

        byte[] header = new byte[44]; // Standard WAV header size

        // RIFF chunk
        writeString(header, 0, "RIFF");     // ChunkID (4 bytes)
        writeInt(header, 4, (int) totalDataLen); // ChunkSize (4 bytes) - total file size - 8 bytes
        writeString(header, 8, "WAVE");     // Format (4 bytes)

        // fmt sub-chunk
        writeString(header, 12, "fmt ");    // Subchunk1ID (4 bytes)
        writeInt(header, 16, 16);           // Subchunk1Size (4 bytes) - 16 for PCM
        writeShort(header, 20, (short) audioFormat); // AudioFormat (2 bytes) - 1 for PCM, 3 for IEEE float
        writeShort(header, 22, (short) channels); // NumChannels (2 bytes) - 1 for mono, 2 for stereo
        writeInt(header, 24, (int) longSampleRate); // SampleRate (4 bytes)
        writeInt(header, 28, (int) byteRate); // ByteRate (4 bytes) - SampleRate * NumChannels * BitsPerSample/8
        writeShort(header, 32, (short) (channels * (bitsPerSample / 8))); // BlockAlign (2 bytes) - NumChannels * BitsPerSample/8
        writeShort(header, 34, (short) bitsPerSample); // BitsPerSample (2 bytes)

        // data sub-chunk
        writeString(header, 36, "data");    // Subchunk2ID (4 bytes)
        writeInt(header, 40, (int) totalAudioLen); // Subchunk2Size (4 bytes) - number of data bytes

        out.write(header, 0, 44); // Write the entire 44-byte header to the stream
    }

    /**
     * Helper to get the number of bits per sample based on the AudioFormat encoding.
     *
     * @param audioEncoding The AudioFormat encoding constant.
     * @return The number of bits per sample (e.g., 8, 16, 32).
     */
    private int getBitsPerSample(int audioEncoding) {
        switch (audioEncoding) {
            case AudioFormat.ENCODING_PCM_8BIT:
                return 8;
            case AudioFormat.ENCODING_PCM_16BIT:
                return 16;
            case AudioFormat.ENCODING_PCM_FLOAT:
                return 32; // IEEE Float is 32-bit
            default:
                Log.w(TAG, "Unknown audio encoding: " + audioEncoding + ". Assuming 16-bit PCM.");
                return 16; // Default to 16-bit for safety
        }
    }

    // --- Helper methods to write data to a byte array in Little Endian format ---
    // WAV format uses Little Endian byte order for multi-byte values.

    private void writeString(byte[] dest, int offset, String s) {
        for (int i = 0; i < s.length(); i++) {
            dest[offset + i] = (byte) s.charAt(i);
        }
    }

    private void writeInt(byte[] dest, int offset, int val) {
        dest[offset + 0] = (byte) (val & 0xff);
        dest[offset + 1] = (byte) ((val >> 8) & 0xff);
        dest[offset + 2] = (byte) ((val >> 16) & 0xff);
        dest[offset + 3] = (byte) ((val >> 24) & 0xff);
    }

    private void writeShort(byte[] dest, int offset, short val) {
        dest[offset + 0] = (byte) (val & 0xff);
        dest[offset + 1] = (byte) ((val >> 8) & 0xff);
    }

    /**
     * Shuts down the internal executor service. This should be called when the
     * AudioRecorderManager instance is no longer needed (e.g., in Activity's onDestroy).
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // Attempts to stop all actively executing tasks
            Log.d(TAG, "Executor service shut down.");
        }
    }
}
