/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.activities.ballot;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.List;

import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.adapters.ballot.BallotOverviewListAdapter;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ballot.BallotModel;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class BallotChooserActivity extends ThreemaToolbarActivity implements ListView.OnItemClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BallotChooserActivity");

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private BallotOverviewListAdapter listAdapter = null;
    private ListView listView;

    private final BallotListener ballotListener = new BallotListener() {
        @Override
        public void onClosed(BallotModel ballotModel) {
        }

        @Override
        public void onModified(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateList());
        }

        @Override
        public void onCreated(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateList());
        }

        @Override
        public void onRemoved(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateList());
        }

        @Override
        public boolean handle(BallotModel ballotModel) {
            return true;
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!dependencies.isAvailable()) {
            finish();
        }
    }

    @Override
    protected boolean initActivity(@Nullable Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        listView = this.findViewById(android.R.id.list);
        listView.setOnItemClickListener(this);
        listView.setDividerHeight(0);

        // add text view if list is empty
        EmptyView emptyView = new EmptyView(this);
        emptyView.setup(R.string.ballot_no_ballots_yet);
        ((ViewGroup) listView.getParent()).addView(emptyView);
        listView.setEmptyView(emptyView);
        final AppBarLayout appBarLayout = findViewById(R.id.appbar);
        appBarLayout.setLiftable(true);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                boolean isAtTop = firstVisibleItem == 0 && (view.getChildCount() == 0 || view.getChildAt(0).getTop() == 0);
                appBarLayout.setLifted(!isAtTop);
            }
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.ballot_copy);
        } else {
            setTitle(R.string.ballot_copy);
        }

        this.setupList();
        this.updateList();

        return true;
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(android.R.id.list),
            InsetSides.lbr()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        ListenerManager.ballotListeners.add(this.ballotListener);
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_list_toolbar;
    }

    @Override
    protected void onDestroy() {
        ListenerManager.ballotListeners.remove(this.ballotListener);
        super.onDestroy();
    }

    private void setupList() {
        final ListView listView = this.listView;

        if (listView != null) {
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        }
    }

    private void updateList() {
        try {
            List<BallotModel> ballots = dependencies.getBallotService().getBallots(new BallotService.BallotFilter() {
                @Override
                public MessageReceiver<?> getReceiver() {
                    return null;
                }

                @Override
                public BallotModel.State[] getStates() {
                    return null;
                }

                @Override
                public boolean filter(BallotModel ballotModel) {
                    return true;
                }
            });

            if (ballots != null) {
                this.listAdapter = new BallotOverviewListAdapter(
                    this,
                    ballots,
                    dependencies.getBallotService(),
                    dependencies.getContactService(),
                    Glide.with(this)
                );

                listView.setAdapter(this.listAdapter);
            }
        } catch (NotAllowedException e) {
            logger.error("Exception", e);
            finish();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (this.listAdapter == null) {
            return;
        }

        BallotModel b = listAdapter.getItem(position);

        if (b != null) {
            Intent resultIntent = this.getIntent();
            //append ballot
            IntentDataUtil.append(b, this.getIntent());

            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                break;
        }

        return true;
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
