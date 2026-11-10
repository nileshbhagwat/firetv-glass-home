package dev.glasshome.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Shows an HDMI or AV input full screen through the Android TV input framework.
 * Fire OS reserves its own input player for Amazon-signed apps, so Glass Home plays the
 * input itself in a TvView. Back or Home returns to the launcher.
 */
public final class InputPlayerActivity extends Activity {
    static final String EXTRA_INPUT_ID = "input_id";
    static final String EXTRA_LABEL = "label";
    private static final String TAG = "GlassHome";

    private TvView tvView;
    private TextView status;
    private String label;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        tvView = new TvView(this);
        root.addView(tvView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        status = new TextView(this);
        status.setTextColor(getColor(R.color.text_secondary));
        status.setTextSize(22);
        status.setTypeface(Fonts.outfit(this, 300));
        status.setGravity(Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        label = getIntent().getStringExtra(EXTRA_LABEL);
        showStatus(getString(R.string.input_connecting, label));
        tvView.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onConnectionFailed(String inputId) {
                showStatus(getString(R.string.input_unavailable, label));
            }

            @Override
            public void onDisconnected(String inputId) {
                showStatus(getString(R.string.input_unavailable, label));
            }

            @Override
            public void onVideoAvailable(String inputId) {
                status.setVisibility(View.GONE);
            }

            @Override
            public void onVideoUnavailable(String inputId, int reason) {
                Log.i(TAG, "Input " + inputId + ": no video (reason " + reason + ")");
                showStatus(getString(R.string.input_no_signal, label));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        String inputId = getIntent().getStringExtra(EXTRA_INPUT_ID);
        if (inputId == null) {
            finish();
            return;
        }
        Log.i(TAG, "Opening input " + inputId + " (" + label + ")");
        tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId));
    }

    @Override
    protected void onStop() {
        // Release the input session as soon as the player is no longer visible.
        tvView.reset();
        super.onStop();
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }
}
