/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.util.ArrayList;
import java.util.HashMap;

public class SettingsBlockedUsers extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private boolean loading;
    private View progressView;
    private View emptyView;
    private ArrayList<TLRPC.TL_contactBlocked> blockedContacts = new ArrayList<TLRPC.TL_contactBlocked>();
    private HashMap<Integer, TLRPC.TL_contactBlocked> blockedContactsDict = new HashMap<Integer, TLRPC.TL_contactBlocked>();
    private int selectedUserId;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        loadBlockedContacts(0, 200);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.settings_blocked_users_layout, container, false);
            listViewAdapter = new ListAdapter(parentActivity);
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            progressView = fragmentView.findViewById(R.id.progressLayout);
            emptyView = fragmentView.findViewById(R.id.searchEmptyView);
            if (loading) {
                progressView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                listView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                listView.setEmptyView(emptyView);
            }
            listView.setAdapter(listViewAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i < blockedContacts.size()) {
                        UserProfileActivity fragment = new UserProfileActivity();
                        Bundle args = new Bundle();
                        args.putInt("user_id", blockedContacts.get(i).user_id);
                        fragment.setArguments(args);
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "user_" + blockedContacts.get(i).user_id, false);
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i >= blockedContacts.size()) {
                        return true;
                    }
                    selectedUserId = blockedContacts.get(i).user_id;

                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

                    CharSequence[] items = new CharSequence[] {getStringEntry(R.string.Unblock)};

                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (i == 0) {
                                TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock();
                                TLRPC.User user = MessagesController.Instance.users.get(selectedUserId);
                                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                                    req.id = new TLRPC.TL_inputUserForeign();
                                    req.id.user_id = selectedUserId;
                                    req.id.access_hash = user.access_hash;
                                } else {
                                    req.id = new TLRPC.TL_inputUserContact();
                                    req.id.user_id = selectedUserId;
                                }
                                TLRPC.TL_contactBlocked blocked = blockedContactsDict.get(selectedUserId);
                                blockedContactsDict.remove(selectedUserId);
                                blockedContacts.remove(blocked);
                                listViewAdapter.notifyDataSetChanged();
                                ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                    @Override
                                    public void run(TLObject response, TLRPC.TL_error error) {

                                    }
                                }, null, true, RPCRequest.RPCRequestClassGeneric);
                            }
                        }
                    });
                    builder.show().setCanceledOnTouchOutside(true);

                    return true;
                }
            });

            listView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
                }
            });
            emptyView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void loadBlockedContacts(int offset, int count) {
        if (loading) {
            return;
        }
        loading = true;
        TLRPC.TL_contacts_getBlocked req = new TLRPC.TL_contacts_getBlocked();
        req.offset = offset;
        req.limit = count;
        long requestId = ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            if (progressView != null) {
                                progressView.setVisibility(View.GONE);
                            }
                            if (listView != null) {
                                if (listView.getEmptyView() == null) {
                                    listView.setEmptyView(emptyView);
                                }
                            }
                            if (listViewAdapter != null) {
                                listViewAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                }
                final TLRPC.contacts_Blocked res = (TLRPC.contacts_Blocked)response;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        for (TLRPC.User user : res.users) {
                            MessagesController.Instance.users.put(user.id, user);
                        }
                        for (TLRPC.TL_contactBlocked blocked : res.blocked) {
                            if (!blockedContactsDict.containsKey(blocked.user_id)) {
                                blockedContacts.add(blocked);
                                blockedContactsDict.put(blocked.user_id, blocked);
                            }
                        }
                        if (progressView != null) {
                            progressView.setVisibility(View.GONE);
                        }
                        if (listView != null) {
                            if (listView.getEmptyView() == null) {
                                listView.setEmptyView(emptyView);
                            }
                        }
                        if (listViewAdapter != null) {
                            listViewAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
        ConnectionsManager.Instance.bindRequestToGuid(requestId, classGuid);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            if (listView != null) {
                listView.invalidateViews();
            }
        }
    }

    @Override
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setSubtitle(null);
        actionBar.setCustomView(null);
        actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.BlockedUsers) + "</font>"));

        TextView title = (TextView)parentActivity.findViewById(R.id.abs__action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (getSherlockActivity() == null) {
            return;
        }
        if (!firstStart && listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        firstStart = false;
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    public void onCreateOptionsMenu(Menu menu, com.actionbarsherlock.view.MenuInflater inflater) {
        inflater.inflate(R.menu.settings_block_users_bar_menu, menu);
    }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
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
                    listView.setPadding(listView.getPaddingLeft(), height, listView.getPaddingRight(), listView.getPaddingBottom());

                    listView.getViewTreeObserver().removeOnPreDrawListener(this);

                    return false;
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finishFragment();
                break;
            case R.id.block_user:
                ContactsActivity fragment = new ContactsActivity();
                fragment.animationType = 1;
                Bundle bundle = new Bundle();
                bundle.putBoolean("onlyUsers", true);
                bundle.putBoolean("destroyAfterSelect", true);
                bundle.putBoolean("usersAsSections", true);
                bundle.putBoolean("returnAsResult", true);
                fragment.delegate = this;
                fragment.setArguments(bundle);
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "contacts_block", false);
                break;
        }
        return true;
    }

    @Override
    public void didSelectContact(int user_id) {
        if (blockedContactsDict.containsKey(user_id)) {
            return;
        }
        TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
        TLRPC.User user = MessagesController.Instance.users.get(selectedUserId);
        if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
            req.id = new TLRPC.TL_inputUserForeign();
            req.id.access_hash = user.access_hash;
            req.id.user_id = user_id;
        } else {
            req.id = new TLRPC.TL_inputUserContact();
            req.id.user_id = user_id;
        }
        TLRPC.TL_contactBlocked blocked = new TLRPC.TL_contactBlocked();
        blocked.user_id = user_id;
        blocked.date = (int)(System.currentTimeMillis() / 1000);
        blockedContactsDict.put(blocked.user_id, blocked);
        blockedContacts.add(blocked);
        listViewAdapter.notifyDataSetChanged();
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    private class ListAdapter extends BaseAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != blockedContacts.size();
        }

        @Override
        public int getCount() {
            if (blockedContacts.isEmpty()) {
                return 0;
            }
            return blockedContacts.size() + 1;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.messages_search_user_layout, viewGroup, false);
                }
                ContactsActivity.ContactListRowHolder holder = (ContactsActivity.ContactListRowHolder)view.getTag();
                if (holder == null) {
                    holder = new ContactsActivity.ContactListRowHolder(view);
                    view.setTag(holder);
                    Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                    holder.nameTextView.setTypeface(typeface);
                }

                View divider = view.findViewById(R.id.settings_row_divider);
                divider.setVisibility(View.VISIBLE);

                TLRPC.User user = MessagesController.Instance.users.get(blockedContacts.get(i).user_id);

                TLRPC.FileLocation photo = null;
                if (user.first_name.length() != 0 && user.last_name.length() != 0) {
                    holder.nameTextView.setText(Html.fromHtml(user.first_name + " <b>" + user.last_name + "</b>"));
                } else if (user.first_name.length() != 0) {
                    holder.nameTextView.setText(Html.fromHtml("<b>" + user.first_name + "</b>"));
                } else {
                    holder.nameTextView.setText(Html.fromHtml("<b>" + user.last_name + "</b>"));
                }
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
                int placeHolderId = Utilities.getUserAvatarForId(user.id);
                holder.avatarImage.setImage(photo, "50_50", placeHolderId);

                holder.messageTextView.setTextColor(0xff808080);

                if (user.phone != null && user.phone.length() != 0) {
                    holder.messageTextView.setText(PhoneFormat.Instance.format("+" + user.phone));
                } else {
                    holder.messageTextView.setText("Unknown");
                }
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_unblock_info_row_layout, viewGroup, false);
                    registerForContextMenu(view);
                    TextView textView = (TextView)view.findViewById(R.id.info_text_view);
                    Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                    textView.setTypeface(typeface);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if(i == blockedContacts.size()) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return blockedContacts.isEmpty();
        }
    }
}
