// Copyright (c) 2012-2013 Wolf Paulus - Techcasita Productions
package com.techcasita.android.bot3;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * <code>Bot3</code> uses an AIML Service to compute answers:
 * capturing voice input, converting it in to text, creating an answer through AIML, before synthesizing it into speech.
 * In case the AIML contains a Command String, followed by a valid ticker symbol, the most recent
 * quote for that stock is fetched, put into a template, and eventually spoken.
 *
 * @author <a href="mailto:wolf@wolfpaulus.com">Wolf Paulus</a>
 */
public class Bot3 extends Activity implements TextToSpeech.OnInitListener {

    public static final String BUNDLE_KEY_NAME_FOR_MSG = Bot3.class.getName() + "_BUNDLE_KEY";

    private static final int VOICE_RECOGNITION_REQUEST_CODE = R.integer.VOICE_RECOGNITION_REQUEST_CODE;
    private static final String UTTERANCE_ID = Bot3.class.getSimpleName();
    private static final String KEY_WORD = "QUOTE";  // e.g. get the stock quote for g o o g

    private TextView mTV_TTS;
    private TextView mTV_STT;
    private TextToSpeech mTts;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            if (msg.getData() != null && !msg.getData().isEmpty()) {
                String s = msg.getData().getString(Bot3.BUNDLE_KEY_NAME_FOR_MSG);
                if (s != null && 0 < s.length()) {
                    int i = s.indexOf("CMD ");
                    if (0 <= i) {
                        s = s.substring(i + 4, s.endsWith(".") ? s.length() - 1 : s.length());
                        int k = s.indexOf(" ");
                        if (0 < k) {
                            final String cmd = s.substring(0, k);
                            s = s.substring(k + 1).replace(" ", "");
                            if (Bot3.KEY_WORD.equals(cmd)) {
                                new YQuote(mHandler).execute(s);
                            }
                        }
                    } else {
                        Bot3.this.say(s);
                    }
                }
            }
        }
    };

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mTV_STT = (TextView) findViewById(R.id.tv_stt);
        mTV_TTS = (TextView) findViewById(R.id.tv_tts);
        mTV_TTS.setMovementMethod(new ScrollingMovementMethod());
        // Check to see if a recognition activity is present
        List<ResolveInfo> activities = getPackageManager().queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.size() == 0) {
            mTV_TTS.setText(R.string.err_Recognizer);
        }
        // Setup Text to Speech via InitListener
        try {
            mTts = new TextToSpeech(this, this);
        } catch (Throwable t) {
            mTV_TTS.setText(t.getMessage());
        }
    }

    @Override
    public void onInit(final int status) {
        // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
        if (status == TextToSpeech.SUCCESS && mTts != null) {
            mTts.setSpeechRate(1.0f);
            mTts.setPitch(1.5f);
            // Set preferred language to US english.
            // Note that a language may not be available, and the result will indicate this.
            int result = mTts.setLanguage(0 <= mTts.isLanguageAvailable(Locale.UK) ? Locale.UK : Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                mTV_TTS.setText(R.string.err_Language);
            } else {
                startVoiceRecognitionActivity();
            }
            //
            // OnUtteranceCompletedListener
            //
            //noinspection deprecation
            mTts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String s) {
                    startVoiceRecognitionActivity();
                }
            });
        } else {
            mTV_TTS.setText(R.string.err_TextToSpeech);
        }
    }

    /**
     * In case the speech recognizer times out, tapping on the screen restarts
     *
     * @param view <code.View</code> not used
     */
    @SuppressWarnings("UnusedParameters")
    public void onClick(View view) {
        startVoiceRecognitionActivity();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case VOICE_RECOGNITION_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    final ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    final String s = matches.get(0);
                    new AIML_RPC(mHandler).execute(s);
                    mTV_STT.setText(s);
                } else {
                    mTV_STT.setText(R.string.tapScreen);
                    mTV_TTS.setText("");
                }
                break;
            default:
                mTV_STT.setText("");
                mTV_TTS.setText("");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        if (mTts != null) {
            mTts.shutdown();
        }
        super.onDestroy();
    }

    private void say(final String s) {
        final HashMap<String, String> map = new HashMap<String, String>(1);
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
        mTts.speak(s, TextToSpeech.QUEUE_FLUSH, map);

        mTV_TTS.setText(s);
    }

    /**
     * Fire an intent to start the speech recognition activity.
     */
    private void startVoiceRecognitionActivity() {
        final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // Specify the calling package to identify your application
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
        // Display an hint to the user about what he should say.
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getResources().getString(R.string.speakPROMPT));
        // Given an hint to the recognizer about what the user is going to say
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Specify how many results you want to receive. The results will be sorted
        // where the first result is the one with higher confidence.
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        //intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("es").getLanguage());
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }
}
