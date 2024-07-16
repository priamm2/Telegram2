/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BaseFragment;

public class ChatProfileChangeNameActivity extends BaseFragment {
    private EditText firstNameField;
    private View headerLabelView;
    private int chat_id;
    private View doneButton;

    public ChatProfileChangeNameActivity() {
        animationType = 1;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        chat_id = getArguments().getInt("chat_id", 0);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.chat_profile_change_name_layout, container, false);

            TLRPC.Chat currentChat = MessagesController.Instance.chats.get(chat_id);

            firstNameField = (EditText)fragmentView.findViewById(R.id.first_name_field);
            firstNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        doneButton.performClick();
                        return true;
                    }
                    return false;
                }
            });
            firstNameField.setText(currentChat.title);
            firstNameField.setSelection(firstNameField.length());

            TextView headerLabel = (TextView)fragmentView.findViewById(R.id.settings_section_text);
            headerLabel.setText(getStringEntry(R.string.EnterGroupNameTitle));
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        final View view = getView();
        if (view != null) {
            ViewTreeObserver obs = view.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    WindowManager manager = (WindowManager)parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    Display display = manager.getDefaultDisplay();
                    int rotation = display.getRotation();
                    int height;
                    int currentActionBarHeight = parentActivity.getSupportActionBar().getHeight();
                    float density = Utilities.applicationContext.getResources().getDisplayMetrics().density;
                    if (currentActionBarHeight != 48 * density && currentActionBarHeight != 40 * density) {
                        height = currentActionBarHeight;
                    } else {
                        height = (int)(48.0f * density);
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            height = (int)(40.0f * density);
                        }
                    }
                    view.setPadding(view.getPaddingLeft(), height, view.getPaddingRight(), view.getPaddingBottom());

                    view.getViewTreeObserver().removeOnPreDrawListener(this);

                    return false;
                }
            });
        }
    }

    @Override
    public boolean canApplyUpdateStatus() {
        return false;
    }

    @Override
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);

        actionBar.setCustomView(R.layout.settings_do_action_layout);
        View cancelButton = actionBar.getCustomView().findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishFragment();
            }
        });
        doneButton = actionBar.getCustomView().findViewById(R.id.done_button);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (firstNameField.getText().length() != 0) {
                    saveName();
                    finishFragment();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getSherlockActivity() == null) {
            return;
        }
        ((ApplicationActivity)parentActivity).updateActionBar();
        fixLayout();
        SharedPreferences preferences = Utilities.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            firstNameField.requestFocus();
            Utilities.showKeyboard(firstNameField);
        }
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (nextAnim != 0) {
            Animation anim = AnimationUtils.loadAnimation(getActivity(), nextAnim);

            anim.setAnimationListener(new Animation.AnimationListener() {

                public void onAnimationStart(Animation animation) {
                    ChatProfileChangeNameActivity.this.onAnimationStart();
                }

                public void onAnimationRepeat(Animation animation) {

                }

                public void onAnimationEnd(Animation animation) {
                    ChatProfileChangeNameActivity.this.onAnimationEnd();
                    firstNameField.requestFocus();
                    Utilities.showKeyboard(firstNameField);
                }
            });

            return anim;
        } else {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    private void saveName() {
        MessagesController.Instance.changeChatTitle(chat_id, firstNameField.getText().toString());
    }
}
