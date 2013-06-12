package at.jclehner.rxdroid.preferences;

import java.io.Serializable;

import android.content.Context;
import android.util.AttributeSet;
import at.jclehner.androidutils.AdvancedDialogPreference;
import at.jclehner.rxdroid.R;

public abstract class BaseAdvancedDialogPreference<T extends Serializable> extends AdvancedDialogPreference<T>
{
	public BaseAdvancedDialogPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected int getDialogThemeResId() {
		return R.style.Theme_RxDroid_Dialog;
	}
}
