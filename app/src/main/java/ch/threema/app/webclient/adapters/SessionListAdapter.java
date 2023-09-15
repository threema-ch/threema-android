/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.webclient.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.AbstractRecyclerAdapter;
import ch.threema.app.services.BrowserDetectionService.Browser;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.app.webclient.services.SessionService;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.storage.models.WebClientSessionModel;

@UiThread
public class SessionListAdapter extends AbstractRecyclerAdapter<WebClientSessionModel, RecyclerView.ViewHolder> {

	private final Context context;
	private final LayoutInflater inflater;
	private final SessionService sessionService;
	private final String currentPushToken;
	private OnClickItemListener onClickItemListener;
	private final int red, green, orange, grey;

	private static class SessionListViewHolder extends RecyclerView.ViewHolder {
		private final TextView createDateView;
		private final TextView lastUsageView;
		private final CircularProgressIndicator loadingIndicator;
		private final ImageView browserIcon;
		private final ImageView errorIcon;
		private final TextView sessionNameView;
		private final ImageView connectionStatusIcon;
		private final TextView invalidPushToken;
		protected WebClientSessionModel model;

		private SessionListViewHolder(final View itemView) {
			super(itemView);

			this.createDateView = itemView.findViewById(R.id.created_at);
			this.lastUsageView = itemView.findViewById(R.id.last_usage);
			this.loadingIndicator = itemView.findViewById(R.id.session_loading);
			this.browserIcon = itemView.findViewById(R.id.session_browser_icon);
			this.errorIcon = itemView.findViewById(R.id.session_error_icon);
			this.sessionNameView = itemView.findViewById(R.id.session_name);
			this.connectionStatusIcon = itemView.findViewById(R.id.connection_status);
			this.invalidPushToken = itemView.findViewById(R.id.invalid_push_token);
		}

		public View getItem() {
			return itemView;
		}
	}

	public SessionListAdapter(Context context, SessionService sessionService, PreferenceService preferenceService) {
		this.context = context;
		this.inflater = LayoutInflater.from(context);
		this.sessionService = sessionService;

		this.red = context.getResources().getColor(R.color.material_red);
		this.orange = context.getResources().getColor(R.color.material_orange);
		this.green = context.getResources().getColor(R.color.material_green);
		this.grey = context.getResources().getColor(R.color.material_grey_500);

		this.currentPushToken =  preferenceService.getPushToken();
	}

	public interface OnClickItemListener {
		void onClick(WebClientSessionModel model, int position);
	}

	public SessionListAdapter setOnClickItemListener(OnClickItemListener onClickItemListener) {
		this.onClickItemListener = onClickItemListener;
		return this;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int type) {
		View itemView = inflater.inflate(R.layout.item_webclient_session_list, viewGroup, false);
		itemView.setBackgroundResource(R.drawable.listitem_background_selector);
		return new SessionListViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, final int p) {
		final SessionListViewHolder holder = (SessionListViewHolder)h;
		final int position = h.getAdapterPosition();

		final WebClientSessionModel model = this.getEntity(position);
		holder.model = model;
		if(this.onClickItemListener != null) {
			holder.itemView.setOnClickListener(v -> onClickItemListener.onClick(holder.model, h.getAdapterPosition()));
			holder.itemView.setOnLongClickListener(v -> true);
		}

		// Set session name
		String sessionName = model.getLabel();
		holder.sessionNameView.setText(TestUtil.empty(sessionName) ?
				context.getString(R.string.webclient_unnamed_session) :
				sessionName);

		// Show connection state
		final WebClientSessionState state = this.sessionService.getState(model);
		switch (state) {
			case CONNECTED:
				holder.connectionStatusIcon.setColorFilter(this.green, PorterDuff.Mode.SRC_IN);
				break;
			case CONNECTING:
				holder.connectionStatusIcon.setColorFilter(this.orange, PorterDuff.Mode.SRC_IN);
				break;
			case DISCONNECTED:
				holder.connectionStatusIcon.setColorFilter(this.grey, PorterDuff.Mode.SRC_IN);
				break;
			case ERROR:
			default:
				holder.connectionStatusIcon.setColorFilter(this.red, PorterDuff.Mode.SRC_IN);
				break;
		}

		// Set create date and persistence
		int mode = model.isPersistent()
			? R.string.webclient_persistent
			: R.string.webclient_disposable;
		if (model.getCreated() != null) {
			final String timeStampString = LocaleUtil.formatTimeStampString(
				this.context,
				model.getCreated().getTime(),
				true
			);
			holder.createDateView.setText(this.context.getString(
				R.string.webclient_created_at,
				timeStampString,
				this.context.getString(mode)
			));
		} else {
			holder.createDateView.setText(this.context.getString(
				R.string.webclient_created_at,
				this.context.getString(R.string.unknown),
				this.context.getString(mode)
			));
		}

		// Set last usage
		if (model.getLastConnection() != null) {
			final int resId = state == WebClientSessionState.CONNECTED ? R.string.webclient_active_since : R.string.webclient_last_usage;
			final String timeStampString = LocaleUtil.formatTimeStampString(
				this.context,
				model.getLastConnection().getTime(),
				true
			);
			holder.lastUsageView.setText(this.context.getString(resId, timeStampString));
		} else {
			holder.lastUsageView.setText(this.context.getString(
				R.string.webclient_last_usage,
				this.context.getString(R.string.never)
			));
		}
		ViewUtil.show(holder.lastUsageView);

		// Show invalid push token error message
		ViewUtil.show(
			holder.invalidPushToken,
			!TestUtil.empty(model.getPushToken())
			&& !TestUtil.compare(this.currentPushToken, model.getPushToken()));
		switch (model.getState()) {
			case INITIALIZING:
				ViewUtil.show(holder.loadingIndicator, true);
				ViewUtil.show(holder.browserIcon, false);
				ViewUtil.show(holder.errorIcon, false);
				break;
			case ERROR:
				ViewUtil.show(holder.loadingIndicator, false);
				ViewUtil.show(holder.browserIcon, false);
				ViewUtil.show(holder.errorIcon, true);
				break;
			case AUTHORIZED:
				ViewUtil.show(holder.loadingIndicator, false);
				ViewUtil.show(holder.browserIcon, false);
				ViewUtil.show(holder.errorIcon, false);

				// Detect browser based on user agent
				final String userAgent = model.getClientDescription();
				final Browser browser = ThreemaApplication.getServiceManager()
						.getBrowserDetectionService().detectBrowser(userAgent);

				// Set browser icon
				final int browserId;
				switch (browser) {
					case CHROME:
						browserId = R.drawable.browser_chrome;
						break;
					case FIREFOX:
						browserId = R.drawable.browser_firefox;
						break;
					case OPERA:
						browserId = R.drawable.browser_opera;
						break;
					case SAFARI:
						browserId = R.drawable.browser_safari;
						break;
					case EDGE:
						browserId = R.drawable.browser_edge;
						break;
					case WEBTOP:
						browserId = R.drawable.browser_desktop;
						break;
					default:
						browserId = R.drawable.browser_unknown;
						break;
				}

				ViewUtil.showAndSet(holder.browserIcon, browserId);

				break;
		}
	}


}
