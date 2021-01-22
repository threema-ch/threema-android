/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.StepPagerStrip;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.APIConnector;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;

public class BallotWizardActivity extends ThreemaActivity {
	private static final Logger logger = LoggerFactory.getLogger(BallotWizardActivity.class);

	private static final int NUM_PAGES = 2;

	private ViewPager pager;
	private ScreenSlidePagerAdapter pagerAdapter;
	private BallotService ballotService;
	private ContactService contactService;
	private APIConnector apiConnector;
	private GroupService groupService;
	private String identity;
	private StepPagerStrip stepPagerStrip;
	private ImageView nextButton, copyButton, prevButton;
	private Button nextText;
	private MessageReceiver receiver;

	private final List<BallotChoiceModel> ballotChoiceModelList = new ArrayList<>();
	private String ballotTitle;
	private BallotModel.Type ballotType;
	private BallotModel.Assessment ballotAssessment;

	private MessageService messageService;

	private final List<WeakReference<BallotWizardFragment>> fragmentList = new ArrayList<>();
	private final Runnable createBallotRunnable = new Runnable() {
		@Override
		public void run() {
			BallotUtil.createBallot(receiver, ballotTitle, ballotType, ballotAssessment, ballotChoiceModelList);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		ConfigUtils.configureActivityTheme(this);

		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_ballot_wizard);

		pager = findViewById(R.id.pager);
		pagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
		pager.setAdapter(pagerAdapter);

		stepPagerStrip = findViewById(R.id.strip);
		stepPagerStrip.setPageCount(NUM_PAGES);
		stepPagerStrip.setCurrentPage(0);

		copyButton = findViewById(R.id.copy_ballot);
		copyButton.setOnClickListener(v -> startCopy());

		prevButton = findViewById(R.id.prev_page_button);
		prevButton.setOnClickListener(v -> prevPage());

		nextButton = findViewById(R.id.next_page_button);
		nextButton.setOnClickListener(v -> nextPage());

		nextText = findViewById(R.id.next_text);
		nextText.setOnClickListener(v -> nextPage());

		pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int i, float v, int i2) {}

			@Override
			public void onPageSelected(int position) {
				for (WeakReference<BallotWizardFragment> fragment: fragmentList) {
					BallotWizardCallback callback = (BallotWizardCallback) fragment.get();
					if (callback != null) {
						callback.onPageSelected(position);
					}
				}
				if (position == 1) {
					if (checkTitle()) {
						nextButton.setVisibility(View.GONE);
						prevButton.setVisibility(View.VISIBLE);
						nextText.setVisibility(View.VISIBLE);
						copyButton.setVisibility(View.GONE);
					} else {
						position = 0;
					}
				} else {
					nextButton.setVisibility(View.VISIBLE);
					prevButton.setVisibility(View.GONE);
					nextText.setVisibility(View.GONE);
					copyButton.setVisibility(View.VISIBLE);
				}
				stepPagerStrip.setCurrentPage(position);
			}

			@Override
			public void onPageScrollStateChanged(int i) { }
		});

		instantiate();

		setDefaults();
		handleIntent();
	}

	@Override
	protected void onDestroy() {
		synchronized (this.fragmentList) {
			fragmentList.clear();
		}
		super.onDestroy();
	}

	/**
	 * save the attached fragments to update on copy command
	 * @param fragment
	 */
	@Override
	public void onAttachFragment(Fragment fragment) {
		super.onAttachFragment(fragment);

		if(fragment instanceof BallotWizardFragment) {
			synchronized (this.fragmentList) {
				this.fragmentList.add(new WeakReference<>((BallotWizardFragment)fragment));
			}
		}
	}

	private void setDefaults() {
		setBallotType(BallotModel.Type.INTERMEDIATE);
		setBallotAssessment(BallotModel.Assessment.SINGLE_CHOICE);
		setResult(RESULT_CANCELED);
	}

	private void handleIntent() {
		this.receiver = IntentDataUtil.getMessageReceiverFromIntent(this, getIntent());
	}

	@Override
	public void onBackPressed() {
		int currentItem = pager.getCurrentItem();
		if (currentItem == 0) {
			super.onBackPressed();
		} else {
			pager.setCurrentItem(currentItem - 1);
		}
	}

	private boolean checkTitle() {
		if (TestUtil.empty(this.ballotTitle)) {
			BallotWizardCallback callback = (BallotWizardCallback) this.fragmentList.get(0).get();
			if (callback != null) {
				callback.onMissingTitle();
			}
			pager.setCurrentItem(0);
			return false;
		}
		return true;
	}

	public void nextPage() {
		int currentItem = pager.getCurrentItem() + 1;
		if (currentItem < NUM_PAGES) {
			pager.setCurrentItem(currentItem);
		} else {
			/* end */
			if (checkTitle()) {
				BallotWizardFragment1 fragment = (BallotWizardFragment1) pagerAdapter.instantiateItem(pager, pager.getCurrentItem());
				fragment.saveUnsavedData();
				if (this.ballotChoiceModelList.size() > 1) {
					ThreemaApplication.sendMessageExecutorService.execute(createBallotRunnable);
					setResult(RESULT_OK);
					finish();
				} else {
					Toast.makeText(BallotWizardActivity.this, getString(R.string.ballot_answer_count_error), Toast.LENGTH_SHORT).show();
				}
			}
		}
	}

	public void prevPage() {
		pager.setCurrentItem(0);
	}

	public void setBallotTitle(String title) {
		this.ballotTitle = title != null ? title.trim() : title;
	}

	public void setBallotType(BallotModel.Type ballotType) {
		this.ballotType = ballotType;
	}

	public void setBallotAssessment(BallotModel.Assessment ballotAssessment) {
		this.ballotAssessment = ballotAssessment;
	}

	public List<BallotChoiceModel> getBallotChoiceModelList() {
		return this.ballotChoiceModelList;
	}

	public String getBallotTitle() {
		return this.ballotTitle;
	}

	public BallotModel.Type getBallotType() {
		return this.ballotType;
	}
	public BallotModel.Assessment getBallotAssessment() {
		return this.ballotAssessment;
	}

	private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
		public ScreenSlidePagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			switch (position) {
				case 0:
					return new BallotWizardFragment0();
				case 1:
					return new BallotWizardFragment1();
				default:
					break;
			}
			return null;
		}

		@Override
		public int getCount() {
			return NUM_PAGES;
		}
	}

	protected void instantiate() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		if (serviceManager != null) {
			try {
				this.messageService = serviceManager.getMessageService();
				this.ballotService = serviceManager.getBallotService();
				this.contactService = serviceManager.getContactService();
				this.apiConnector = serviceManager.getAPIConnector();
				this.groupService = serviceManager.getGroupService();
				this.identity = serviceManager.getUserService().getIdentity();
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}
		}
	}

	protected boolean checkInstances() {
		return TestUtil.required(
				this.messageService,
				this.ballotService,
				this.apiConnector,
				this.contactService,
				this.groupService,
				this.identity);
	}

	public void startCopy() {
		Intent copyIntent = new Intent(this, BallotChooserActivity.class);
		startActivityForResult(copyIntent, ThreemaActivity.ACTIVITY_ID_COPY_BALLOT);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == Activity.RESULT_OK) {
			if(requestCode == ThreemaActivity.ACTIVITY_ID_COPY_BALLOT) {
				//get the ballot to copy
				int ballotToCopyId = IntentDataUtil.getBallotId(data);
				if(ballotToCopyId > 0 && this.requiredInstances()) {
					BallotModel ballotModel = this.ballotService.get(ballotToCopyId);
					if(ballotModel != null) {
						this.copyFrom(ballotModel);
					}
					else {
						logger.error("not a valid ballot model");
					}
				}
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void copyFrom(BallotModel ballotModel) {
		if(ballotModel != null && this.requiredInstances()) {
			this.ballotTitle = ballotModel.getName();
			this.ballotType = ballotModel.getType();
			this.ballotAssessment = ballotModel.getAssessment();

			this.ballotChoiceModelList.clear();

			try {
				for(BallotChoiceModel ballotChoiceModel: this.ballotService.getChoices(ballotModel.getId())) {
					BallotChoiceModel choiceModel = new BallotChoiceModel();
					choiceModel.setName(ballotChoiceModel.getName());
					choiceModel.setType(ballotChoiceModel.getType());
					this.ballotChoiceModelList.add(choiceModel);
				}
			} catch (NotAllowedException e) {
				//cannot get choices
				logger.error("Exception", e);
			}

			//goto first page
			pager.setCurrentItem(0);

			//loop all active fragments
			for(WeakReference<BallotWizardFragment> ballotFragment : this.fragmentList) {
				BallotWizardFragment f = ballotFragment.get();
				if(f != null && f.isAdded()) {
					f.updateView();
				}
			}
		}
	}

	public interface BallotWizardCallback {
		void onMissingTitle();
		void onPageSelected(int page);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
}
