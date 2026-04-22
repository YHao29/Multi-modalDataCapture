package com.lannooo.audiocenter.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.lannooo.audiocenter.R;
import com.lannooo.audiocenter.audio.ClientAudioHandler;
import com.lannooo.audiocenter.audio.UltrasonicConfig;
import com.lannooo.audiocenter.tool.MessageUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class ClientService extends Service {
    public static final String TAG = "ClientService";
    public static final long WAKELOCK_INTERVAL_MILLI = 10 * 6 * 1000L;

    private final IBinder binder = new ClientBinder();
    private ExecutorService executor;
    private ScheduledExecutorService scheduledExecutor;
    private Bootstrap bootstrap;
    private int port;
    private String ip;
    private Channel channel;
    private MessageListener listener;
    private ClientAudioHandler audioHandler;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private volatile long lastRequestTime;

    public ClientAudioHandler getAudioHandler() { return audioHandler; }
    public MessageListener getListener() { return listener; }
    public ExecutorService getExecutor() { return executor; }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        initTaskExecutor();
        initNettyBootstrap();
        initAudioFeatures();
        startForegroundWithNotification();
    }

    private void initAudioFeatures() {
        audioHandler = new ClientAudioHandler(this, this.executor);
    }

    @Override
    public void onDestroy() {
        if (audioHandler != null) {
            audioHandler.stopRecorder();
        }
        if (executor != null) {
            executor.shutdown();
        }
        try {
            disconnect(false);
        } catch (InterruptedException e) {
            Log.e(TAG, "Client channel closing failed: " + e.getMessage());
        }
        releaseWakeLock();
    }

    private void initTaskExecutor() {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    private void initNettyBootstrap() {
        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap = new Bootstrap()
                .channel(NioSocketChannel.class)
                .group(group)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(4096, 8, 4, 0, 0));
                        pipeline.addLast(new ClientEncoder());
                        pipeline.addLast(new ClientDecoder());
                        pipeline.addLast(new ClientHandler(ClientService.this));
                    }
                });
    }

    private void startForegroundWithNotification() {
        String channelName = "Tcp Client Service";
        String channelId = getPackageName();
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLightColor(Color.BLUE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("Tcp Client Service")
                .setContentText("Tcp Client Service is running in the foreground")
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            int foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            startForeground(1, notification, foregroundServiceType);
        } else {
            startForeground(1, notification);
        }
    }

    private boolean connect() throws InterruptedException {
        try {
            ChannelFuture channelFuture = bootstrap.connect(new InetSocketAddress(ip, port));
            channel = channelFuture.sync().channel();
            sendRegisterMessage();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Client connect failed: " + e.getMessage(), e);
            return false;
        }
    }

    private void disconnect(boolean block) throws InterruptedException {
        if (channel != null) {
            ChannelFuture future = channel.close();
            if (block) {
                future.sync();
            }
        }
    }

    public void updateRequestTime() {
        lastRequestTime = System.currentTimeMillis();
    }

    @SuppressLint("WakelockTimeout")
    public void acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioCenter::MyWakelockTag");
            wakeLock.acquire();
        } else if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        if (wifiLock == null) {
            wifiLock = ((WifiManager) getSystemService(WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "AudioCenter::MyWifiLockTag");
            wifiLock.acquire();
        } else if (!wifiLock.isHeld()) {
            wifiLock.acquire();
        }
    }

    public void releaseWakeLock() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        if (wifiLock != null) {
            wifiLock.release();
            wifiLock = null;
        }
    }

    private boolean isChannelReady() {
        return channel != null && channel.isActive();
    }

    private void sendRegisterMessage() throws InterruptedException {
        if (isChannelReady()) {
            Message message = new Message(Message.MessageType.REQUEST, MessageUtil.registerRequest(audioHandler.buildRegisterRouteInfo()).getBytes());
            channel.writeAndFlush(message).sync();
            if (listener != null) {
                listener.onMessageReceived(true, message.getType(), message.toString());
            }
        }
    }

    public class ClientBinder extends Binder {
        public void setMessageListener(MessageListener listener) {
            ClientService.this.listener = listener;
        }

        public boolean connect(final String ip, final int port) throws InterruptedException {
            ClientService.this.ip = ip;
            ClientService.this.port = port;
            return ClientService.this.connect();
        }

        public void disconnect() throws InterruptedException {
            ClientService.this.disconnect(true);
        }

        public void sendRegisterMessage() throws InterruptedException {
            ClientService.this.sendRegisterMessage();
        }

        public void updateManualUltrasonicConfig(boolean enabled,
                                                 int sampleRateHz,
                                                 double startFreqHz,
                                                 double endFreqHz,
                                                 int chirpDurationMs,
                                                 int idleDurationMs,
                                                 double amplitude,
                                                 String windowType,
                                                 boolean repeat) {
            if (audioHandler != null) {
                UltrasonicConfig config = UltrasonicConfig.manual(true, sampleRateHz, startFreqHz, endFreqHz, chirpDurationMs, idleDurationMs, amplitude, windowType, repeat);
                audioHandler.setManualUltrasonicConfig(config, enabled);
            }
        }

        public String getRoutePresetName() {
            return audioHandler == null ? "" : audioHandler.getRoutePresetName();
        }

        public String getRouteCalibrationStatus() {
            return audioHandler == null ? "unknown" : audioHandler.getRouteCalibrationStatus();
        }

        public String getRouteDiagnosticsText() {
            return audioHandler == null ? "" : audioHandler.getRouteDiagnosticsText();
        }

        public String getSavedRouteOutputDeviceId() {
            return audioHandler == null ? "" : audioHandler.getSavedRouteOutputDeviceId();
        }

        public String getSavedRouteInputDeviceId() {
            return audioHandler == null ? "" : audioHandler.getSavedRouteInputDeviceId();
        }

        public String getRouteDeviceIdentitySummary() {
            return audioHandler == null ? "" : audioHandler.getRouteDeviceIdentitySummary();
        }

        public void saveRouteCalibration(String outputDeviceId, String inputDeviceId) {
            if (audioHandler != null) {
                audioHandler.saveRouteCalibration(outputDeviceId, inputDeviceId);
            }
        }
    }
}
