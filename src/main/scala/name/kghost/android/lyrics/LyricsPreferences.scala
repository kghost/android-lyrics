package name.kghost.android.lyrics;

import android.os.Bundle;
import android.preference.PreferenceActivity;

class LyricsPreferences extends PreferenceActivity {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.preferences)
  }
}
