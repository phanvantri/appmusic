package mediaplayer.patryk.mediaplayerpatryk;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.socks.library.KLog;
import com.squareup.picasso.Picasso;

public class MainActivity extends AppCompatActivity {

    private ImageView ivCover;
    private SeekBar sbProgress;
    private TextView tvTime;
    private TextView tvDuration;

    private Playlist playlist;
    private Song song;
    private GuiReceiver receiver;
    private Handler handler = new Handler();
    private boolean blockGUIUpdate;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playlist = PlaylistHandler.getPlaylist(this, "playlistName");
        song = playlist.getSongs().get(0);

        KLog.d(getIntent().getAction());

        if (!isMyServiceRunning(PlayerService.class)) {
            PlayerService.startActionSetPlaylist(this, playlist.getName(), 0);
            PlayerService.startActionPlay(this);
        }

        initilizeViews();
    }

    private void initilizeViews() {
        ivCover = (ImageView) findViewById(R.id.ivCover);
        Picasso.with(this).load(song.getImageUrl()).fit().centerCrop().into(ivCover);

        tvTime = (TextView) findViewById(R.id.tvTime);
        String stringActualTime = String.format("%02d:%02d", 0, 0);
        tvTime.setText(stringActualTime);


        long s = song.getDuration() % 60;
        long m = (song.getDuration() / 60) % 60;
        long h = song.getDuration() / 3600;

        String stringTotalTime;
        if (h != 0)
            stringTotalTime = String.format("%02d:%02d:%02d", h, m, s);
        else
            stringTotalTime = String.format("%02d:%02d", m, s);
        tvDuration = (TextView) findViewById(R.id.tvDuration);
        tvDuration.setText(stringTotalTime);

        sbProgress = (SeekBar) findViewById(R.id.seekBar);
        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int time;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                time = progress;

                sbProgress.setProgress(this.time);
                if (fromUser)
                    tvTime.setText(getTimeString(time));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                blockGUIUpdate = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                unblockGUIUpdate();
                setTime(time);
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (receiver == null) {
            receiver = new GuiReceiver();
            receiver.setPlayerActivity(this);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(PlayerService.GUI_UPDATE_ACTION);
        filter.addAction(PlayerService.NEXT_ACTION);
        filter.addAction(PlayerService.PREVIOUS_ACTION);
        filter.addAction(PlayerService.PLAY_ACTION);
        filter.addAction(PlayerService.PAUSE_ACTION);
        filter.addAction(PlayerService.LOADED_ACTION);
        filter.addAction(PlayerService.LOADING_ACTION);
        filter.addAction(PlayerService.DELETE_ACTION);
        filter.addAction(PlayerService.COMPLETE_ACTION);
        registerReceiver(receiver, filter);

        PlayerService.startActionSendInfoBroadcast(this);
    }

    private void setTime(int time) {
        PlayerService.startActionSeekTo(this, time * 1000);
    }

    private void unblockGUIUpdate() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                blockGUIUpdate = false;
            }
        }, 150);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiver != null)
            unregisterReceiver(receiver);
    }

    public void play(View view) {
        PlayerService.startActionPlay(this);
    }

    public void pause(View view) {
        PlayerService.startActionPause(this);
    }

    public void next(View view) {
        PlayerService.startActionNextSong(this);
        PlayerService.startActionSendInfoBroadcast(this);
    }

    public void previous(View view) {
        PlayerService.startActionPreviousSong(this);
        PlayerService.startActionSendInfoBroadcast(this);
    }

    public void testNotification(View view) {
        final Intent emptyIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 555, emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.exo_controls_fastforward)
                        .setContentTitle("My notification")
                        .setContentText("Hello World!")
                        .setContentIntent(pendingIntent);

        mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(127, mBuilder.build());
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private static String getTimeString(int totalTime) {
        long s = totalTime % 60;
        long m = (totalTime / 60) % 60;
        long h = totalTime / 3600;

        String stringTotalTime;
        if (h != 0)
            stringTotalTime = String.format("%02d:%02d:%02d", h, m, s);
        else
            stringTotalTime = String.format("%02d:%02d", m, s);
        return stringTotalTime;
    }

    private static class GuiReceiver extends BroadcastReceiver {

        private MainActivity playerActivity;
        private int actualTime;

        public void setPlayerActivity(MainActivity playerActivity) {
            this.playerActivity = playerActivity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PlayerService.GUI_UPDATE_ACTION)) {
                if (intent.hasExtra(PlayerService.TOTAL_TIME_VALUE_EXTRA)) {
                    int totalTime = intent.getIntExtra(PlayerService.TOTAL_TIME_VALUE_EXTRA, 0) / 1000;
                    if (playerActivity.sbProgress != null)
                        playerActivity.sbProgress.setMax(totalTime);
                    String stringTotalTime = getTimeString(totalTime);
                    if (playerActivity.tvDuration != null)
                        playerActivity.tvDuration.setText(stringTotalTime);
                }

                if (intent.hasExtra(PlayerService.ACTUAL_TIME_VALUE_EXTRA)) {
                    if (playerActivity.blockGUIUpdate)
                        return;

                    actualTime = intent.getIntExtra(PlayerService.ACTUAL_TIME_VALUE_EXTRA, 0) / 1000;

                    String time = getTimeString(actualTime);

                    if (playerActivity.sbProgress != null) {
                        playerActivity.sbProgress.setProgress(actualTime);
                    }
                    if (playerActivity.tvTime != null)
                        playerActivity.tvTime.setText(time);
                }

                if (intent.hasExtra(PlayerService.COVER_URL_EXTRA)) {
                    String cover = intent.getStringExtra(PlayerService.COVER_URL_EXTRA);
                    Picasso.with(playerActivity).load(cover).fit().centerCrop().into(playerActivity.ivCover);
                }

                if (intent.hasExtra(PlayerService.SONG_NUM_EXTRA)) {
                    int num = intent.getIntExtra(PlayerService.SONG_NUM_EXTRA, 0);
                    playerActivity.song = playerActivity.playlist.getSongs().get(num);
                }
            }
            if (intent.getAction().equals(PlayerService.DELETE_ACTION))
                if (playerActivity != null)
                    playerActivity.finish();
                else
                    PlayerService.startActionSendInfoBroadcast(playerActivity);
        }
    }

}
