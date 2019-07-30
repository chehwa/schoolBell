package mobile.canthouniv;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    protected static final String TAG = "TestSignalGenerator";
    private ToggleButton mPlayButton;
    private AudioManager mAudioManager;
    private int mVolume = 6;
    private MusicIntentReceiver myReceiver;
    private String mFileName;
    private MediaPlayer mPlayer;
    private final int duration = 20;
    private final int sampleRate = 44100;
    private final int numSamples = duration * sampleRate;
    private int recordTimeInMillis;
    private final double sample[] = new double[numSamples];
    private final double freqOfTone = 1000;
    private final byte generatedSnd[] = new byte[2 * numSamples];
    double amplitude = 1.0;
    double twoPi = 2. * Math.PI;
    int f1 = 20;
    int f2 = 20000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPlayButton = (ToggleButton) findViewById(R.id.play_button);
        //play sound 사용 해제 toggleButton

        mPlayButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                Log.i(TAG, "버튼 눌림");

                if(isStoragePermissionGranted()) {
                    if(isChecked) {
                        startPlaying();
                        Toast.makeText(MainActivity.this, "signal out", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        stopPlaying();
                    }
                }
                else {
                    Toast.makeText(MainActivity.this, "You need to get permission to make signal", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mPlayButton.setEnabled(false);

        mAudioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        setVolumeControlStream(mAudioManager.STREAM_MUSIC);

        Spinner spinner = (Spinner) findViewById(R.id.signals_spinner);
        spinner.setOnItemSelectedListener(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.testing_signals, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        final TextView tv = (TextView) findViewById(R.id.text_volume);
        tv.setText(String.valueOf(mVolume));

        //plugged 확인하는 broadcast receiver
        myReceiver = new MusicIntentReceiver();
    }

    AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                mAudioManager.abandonAudioFocus(afChangeListener);
                if (null != mPlayer && mPlayer.isPlaying())
                    stopPlaying();
            }
        }
    };

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.file:
                setSignals();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //녹음 관련이라 나중에 다 지울 수도 있음 한번 더 확인
    public void setSignals() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.dialog_signal_setttings, (ViewGroup) findViewById(R.id.dialog_view));
        final TextView textViewDurationSeconds = (TextView)layout.findViewById(R.id.duration_Seconds);

        final SeekBar sbDurationTime = (SeekBar) layout.findViewById(R.id.seekBarDurationTime);

        Button btnCancel = (Button) layout.findViewById(R.id.btnCancel);
        Button btnOk = (Button) layout.findViewById(R.id.btnOk);

        AlertDialog.Builder builder = new AlertDialog.Builder(this).setView(layout);

        final AlertDialog alertDialog = builder
                .setCancelable(false)
                .create();

        final SharedPreferences sharedPreferences = getSharedPreferences("timePreference", Context.MODE_PRIVATE);
        int durationTime = sharedPreferences.getInt("durationTime", 30);

        sbDurationTime.setProgress(durationTime);

        textViewDurationSeconds.setText(String.valueOf(sbDurationTime.getProgress()));

        sbDurationTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int mProgress;
            int min = 20; //minimum duration

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mProgress = progress;

                if (progress < min){
                    progress = min;
                }
                try {
                    textViewDurationSeconds.setText(String.valueOf(progress));
                } catch (NullPointerException e){
                    Log.e(TAG, e.toString());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mProgress < min){
                    sbDurationTime.setProgress(min);
                }
            }
        });

        alertDialog.show();

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
            }
        });

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("durationTime", sbDurationTime.getProgress());
                editor.apply();
                Log.d(TAG, "Duration time saved in sec: " + sbDurationTime.getProgress());
                alertDialog.dismiss();
            }
        });
    }

    private void onPlayPressed(boolean shouldStartPlaying) {
        if (shouldStartPlaying) {
            startPlaying();
        }
        else {
            stopPlaying();
        }
    }

    private void startPlaying() {
        if (mFileName != null) {
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setDataSource(mFileName);
                mPlayer.prepare();
                mPlayer.start();
            } catch (IOException e) {
                Log.e(TAG, "Media Player를 실행할 수 없습니다.");
            }
        } else {
            Log.e(TAG, "첫번째 테스트 시그널 발생");
        }
    }

    private void stopPlaying() {
        if (null != mPlayer) {
            if (mPlayer.isPlaying())
                mPlayer.stop();
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        genSignals(pos);
        byteConversion();
        if(isStoragePermissionGranted()){
            mPlayButton.setEnabled(true);
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        genTone();
        mPlayButton.setEnabled(false);
    }

    protected void onResume() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(myReceiver, filter);
        super.onResume();
    }

    //사인파 발생
    void genTone() {
        Log.i(TAG, "genTone 실행");
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freqOfTone));
        }
    }

    void genSweepTone(double f1, double f2) {
        //0Hz 제외
        if (f1 < 1)
            f1 = 1.;
        else if (f2 > f1)
            Log.i(TAG, "swept tone signal 발생");
        else {
            Log.e(TAG, "f1, f2 정의 오류");
        }
        // log2로 변환
        double b1 = Math.log10(f1) / Math.log10(2.);
        double b2 = Math.log10(f2) / Math.log10(2.);
        // log2 범위 설정
        double rb = b2 - b1;
        // defining step by time resolution
        double step = rb / numSamples;
        double nf = b1;   // new frequency
        for (int i = 0; i < numSamples; i++) {
            double time = i * 1.0 / sampleRate;
            double f = Math.pow(2., nf);
            sample[i] = (amplitude * Math.sin(twoPi * f * time));
            nf = nf + step;
        }
    }

    //White Noise
    void generateWhiteNoise() {
        Log.i(TAG, "White Noise");
        double Max = amplitude;
        double Min = -amplitude;
        for (int i = 0; i < numSamples; i++) {
            sample[i] = (Math.random() * (Max - Min)) - 1.;
        }
    }

    //MLS
    void generateMLS(int N) {
        Log.i(TAG, "MLS");
        // Initialize abuff array to ones
        // Generate pseudo random signal
        int nsamp = (int) Math.pow(2, N);
        int taps = 4, tap1 = 1, tap2 = 2, tap3 = 4, tap4 = 15;
        if (N != 16) {
            Log.e(TAG, "At this moment MLS signal is only defined for 16 bits, soon other tap values will be included.");
        }
        int[] abuff = new int[N];

        for (int i = 0; i < abuff.length; i++) {
            abuff[i] = 1;
        }
        for (int i = nsamp; i > 1; i--) {

            int xorbit = abuff[tap1] ^ abuff[tap2];

            if (taps == 4) {
                int xorbit2 = abuff[tap3] ^ abuff[tap4];
                xorbit = xorbit ^ xorbit2;
            }

            for (int j = N - 1; j > 0; j--) {
                int temp = abuff[j - 1];
                abuff[j] = temp;
            }
            abuff[0] = xorbit;

            sample[i] = (-2. * xorbit) + 1.;
        }
    }

    void byteConversion() {
        int idx = 0;
        for (final double dVal : sample) {
            final short val = (short) ((dVal * 32767));
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
    }

    void genSignals(int pos) {
        switch (pos) {
            case 0: {
                genTone();
                break;
            }
            case 1: {
                genSweepTone(f1, f2);
                break;
            }
            case 2: {
                generateWhiteNoise();
                break;
            }
            case 3: {
                generateMLS(16);
                break;
            }
        }
    }

    private class MusicIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        Log.d(TAG, "Headset is unplugged");
                        // unable Play button if headset is unplugged
                        Toast.makeText(MainActivity.this, "Headset is unplugged",
                                Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        Log.d(TAG, "Headset is plugged");
                        mPlayButton.setEnabled(true);
                        break;
                    default:
                        Log.d(TAG, "I have no idea what the headset state is");
                }
            }
        }
    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
                return true;
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Storage Permission is granted");
            return true;
        }
    }

}
