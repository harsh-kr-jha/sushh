package org.briarproject.briar.android.privategroup.conversation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.briar.android.threaded.ThreadPostViewHolder;
import org.briarproject.nullsafety.NotNullByDefault;

import androidx.annotation.UiThread;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@UiThread
@NotNullByDefault
class GroupMessageItemViewHolder extends
		ThreadPostViewHolder<GroupMessageItem> {

	interface PinListener extends ThreadItemListener<GroupMessageItem> {
		void onPinClick(GroupMessageItem item);
	}

	private final ImageView image;
	private final TextView pin;
	private final TextView adminRequest;
	private final boolean canPin;

	GroupMessageItemViewHolder(View view, boolean canPin) {
		super(view);
		this.canPin = canPin;
		image = view.findViewById(R.id.channel_image);
		pin = view.findViewById(R.id.btn_pin);
		adminRequest = view.findViewById(R.id.admin_request_label);
	}

	@Override
	public void bind(GroupMessageItem item,
			ThreadItemListener<GroupMessageItem> listener) {
		super.bind(item, listener);
		byte[] bytes = item.getImage();
		if (bytes == null) {
			image.setImageDrawable(null);
			image.setVisibility(GONE);
		} else {
			Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0,
					bytes.length);
			image.setImageBitmap(bitmap);
			image.setVisibility(bitmap == null ? GONE : VISIBLE);
		}
		adminRequest.setVisibility(item.isAdminRequest() ? VISIBLE : GONE);
		pin.setVisibility(canPin && !item.isAdminRequest() ? VISIBLE : GONE);
		if (pin.getVisibility() == VISIBLE) {
			pin.setOnClickListener(v ->
					((PinListener) listener).onPinClick(item));
		} else {
			pin.setOnClickListener(null);
		}
	}

}
