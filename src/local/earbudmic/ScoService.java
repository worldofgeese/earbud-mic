package local.earbudmic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Keeps the Bluetooth headset's call link (SCO) open so every app's ordinary
 * microphone recording uses the headset mic.
 *
 * Android sends MIC/DEFAULT-source recordings to the SCO headset mic only while the
 * communication device is Bluetooth SCO. An app's communication-device request counts
 * only while that app plays or records audio, so this service plays silence while on.
 * It never changes the audio mode, so the phone does not enter a call state.
 */
public final class ScoService extends Service {
    static final String ACTION_STOP = "local.earbudmic.STOP";
    private static final String TAG = "EarbudMic";
    private static final String CHANNEL = "status";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_FRAMES = SAMPLE_RATE / 10; // 100 ms

    /** True while the headset mic route is held. Read by the tile. */
    static volatile boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private AudioTrack silence;
    private Thread writer;
    private volatile boolean playing;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            if (findSco(audio) == null) {
                Log.i(TAG, "Bluetooth headset gone, turning off");
                stopSelf();
            }
        }
    };

    static AudioDeviceInfo findSco(AudioManager audio) {
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                return device;
            }
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // Required promptly after startForegroundService(), even when bailing out below.
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        if (running) {
            return START_NOT_STICKY;
        }

        audio = getSystemService(AudioManager.class);
        AudioDeviceInfo sco = findSco(audio);
        if (sco == null) {
            Log.w(TAG, "No Bluetooth headset with a call profile is connected");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startSilence();
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot play the silent stream", e);
            if (silence != null) {
                silence.release();
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!audio.setCommunicationDevice(sco)) {
            Log.w(TAG, "Android refused the headset as communication device");
            stopSilence();
            stopSelf();
            return START_NOT_STICKY;
        }
        audio.registerAudioDeviceCallback(deviceCallback, main);
        running = true;
        Log.i(TAG, "Headset mic route on: " + sco.getProductName());
        MicTileService.refresh(this);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (running) {
            audio.unregisterAudioDeviceCallback(deviceCallback);
            audio.clearCommunicationDevice();
            stopSilence();
            running = false;
            Log.i(TAG, "Headset mic route off");
        }
        MicTileService.refresh(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startSilence() {
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        int minBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        silence = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                // At least two chunks, so a write after stop() always fits the drained buffer.
                .setBufferSizeInBytes(Math.max(minBytes, CHUNK_FRAMES * 2 * 2))
                .build();
        playing = true;
        silence.play();
        writer = new Thread(this::writeSilence, "silence");
        writer.start();
    }

    private void writeSilence() {
        short[] chunk = new short[CHUNK_FRAMES];
        while (playing) {
            if (silence.write(chunk, 0, chunk.length) < 0 && playing) {
                Log.w(TAG, "Silent stream failed, turning off");
                main.post(this::stopSelf);
                return;
            }
        }
    }

    private void stopSilence() {
        playing = false;
        silence.stop(); // wakes a blocked write()
        try {
            writer.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        silence.release();
    }

    private Notification notification() {
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Earbud mic status", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, ScoService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Earbud mic is on")
                .setContentText("Apps record from your headset. Audio is call quality until you turn this off.")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Turn off", stop).build())
                .build();
    }
}
