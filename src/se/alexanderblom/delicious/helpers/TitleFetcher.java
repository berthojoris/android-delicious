package se.alexanderblom.delicious.helpers;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import se.alexanderblom.delicious.R;
import se.alexanderblom.delicious.R.id;
import se.alexanderblom.delicious.R.string;
import se.alexanderblom.delicious.util.AbstractTextWatcher;
import se.alexanderblom.delicious.util.AsyncLoader;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

public class TitleFetcher implements LoaderCallbacks<String> {
	private static final String TAG = "TitleFetcher";
	
	private Activity activity;
	
	private EditText urlView;
	private EditText titleView;
	
	private TextWatcher titleWatcher = new AbstractTextWatcher() {
		@Override
		public void afterTextChanged(Editable s) {
			// If title is changed when we are fetching it, abort
			activity.getLoaderManager().destroyLoader(0);
			
			Log.d(TAG, "Got input, cancelling title fetch");
		}
	};
	
	public TitleFetcher(Activity activity) {
		this.activity = activity;
		
		urlView = (EditText) activity.findViewById(R.id.url);
        titleView = (EditText) activity.findViewById(R.id.title);
		
        urlView.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_NEXT) {
					maybeFetchTitle();
				}
				
				// Return false to have the keyboard to the the next field
				return false;
			}
		});
	}
	
	
	@Override
	public Loader<String> onCreateLoader(int id, Bundle args) {
		titleView.setHint(R.string.field_title_fetching);
		
		titleView.addTextChangedListener(titleWatcher);
		
		String url = urlView.getText().toString();
		return new TitleLoader(activity, url);
	}

	@Override
	public void onLoadFinished(Loader<String> loader, String title) {
		// We don't need to watch the title anymore
		titleView.removeTextChangedListener(titleWatcher);
		
		titleView.setHint(R.string.field_title);
		titleView.setText(title);
		
		// Select text so the user can easily change it
		titleView.selectAll();
		
		if (title == null) {
			Toast.makeText(activity, R.string.field_title_fetching_failed, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onLoaderReset(Loader<String> loader) {
		// We don't need to watch the title anymore
		titleView.removeTextChangedListener(titleWatcher);
	}
	
	public void maybeFetchTitle() {
		if (TextUtils.isEmpty(titleView.getText().toString())
				&& Patterns.WEB_URL.matcher(urlView.getText().toString()).find()) {
			fetchTitle();
		}
	}
	
	private void fetchTitle() {
		activity.getLoaderManager().restartLoader(0, null, this);
	}
	
	private static class TitleLoader extends AsyncLoader<String> {
		private static final String TAG = "TitleLoader";
		
		private static final Pattern PATTERN_TITLE = Pattern.compile("<title>(.*?)</title>");
		
		private String url;
		
		public TitleLoader(Context context, String url) {
			super(context);
			
			this.url = url;
		}

		@Override
		public String loadInBackground() {
			try {
				HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
				
				try {
					InputStreamReader reader = new InputStreamReader(new BufferedInputStream(request.getInputStream()), Charsets.UTF_8);
					String html = CharStreams.toString(reader);

					Matcher m = PATTERN_TITLE.matcher(html);
					if (m.find()) {
						return m.group(1).trim();
					} else {
						return null;
					}
				} finally {
					request.disconnect();
				}
			} catch (IOException e) {
				Log.e(TAG, "Failed to fetch title for: " + url, e);
				
				return null;
			}
		}
	}
}