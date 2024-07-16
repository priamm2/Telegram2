/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.AvatarUpdater;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class ChatProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private int chat_id;
    private String selectedPhone;
    private TLRPC.ChatParticipants info;
    private TLRPC.TL_chatParticipant selectedUser;
    private AvatarUpdater avatarUpdater = new AvatarUpdater();
    private int totalMediaCount = -1;
    private int onlineCount = -1;
    private ArrayList<Integer> sortedUsers = new ArrayList<Integer>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.addObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.closeChats);

        chat_id = getArguments().getInt("chat_id", 0);
        info = (TLRPC.ChatParticipants)NotificationCenter.Instance.getFromMemCache(5);
        updateOnlineCount();
        MessagesController.Instance.getMediaCount(-chat_id, classGuid, true);
        avatarUpdater.delegate = new AvatarUpdater.AvatarUpdaterDelegate() {
            @Override
            public void didUploadedPhoto(TLRPC.TL_inputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big) {
                if (chat_id != 0) {
                    MessagesController.Instance.changeChatAvatar(chat_id, file);
                }
            }
        };
        avatarUpdater.parentFragment = this;
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.removeObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.closeChats);
        avatarUpdater.clear();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.chat_profile_layout, container, false);

            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listViewAdapter = new ListAdapter(parentActivity));
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    int size = 0;
                    if (info != null) {
                        size += info.participants.size();
                    }
                    if (i > 6 && i < size + 7) {
                        TLRPC.TL_chatParticipant user = info.participants.get(sortedUsers.get(i - 7));
                        if (user.user_id == UserConfig.clientUserId) {
                            return false;
                        }
                        if (info.admin_id != UserConfig.clientUserId && user.inviter_id != UserConfig.clientUserId) {
                            return false;
                        }
                        selectedUser = user;

                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        CharSequence[] items = new CharSequence[] {getStringEntry(R.string.KickFromGroup)};

                        builder.setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == 0) {
                                    kickUser(selectedUser);
                                }
                            }
                        });
                        builder.show().setCanceledOnTouchOutside(true);

                        return true;
                    }
                    return false;
                }
            });

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i == 2) {
                        SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        String key = "notify_" + (-chat_id);
                        boolean value = preferences.getBoolean(key, true);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean(key, !value);
                        editor.commit();
                        listView.invalidateViews();

                        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
                        req.settings = new TLRPC.TL_inputPeerNotifySettings();
                        req.settings.sound = "";
                        req.peer = new TLRPC.TL_inputNotifyPeer();
                        ((TLRPC.TL_inputNotifyPeer)req.peer).peer = new TLRPC.TL_inputPeerChat();
                        ((TLRPC.TL_inputNotifyPeer)req.peer).peer.chat_id = chat_id;
                        req.settings.show_previews = true;
                        req.settings.events_mask = 1;
                        if (value) {
                            req.settings.mute_until = (int)(System.currentTimeMillis() / 1000) + 10 * 365 * 24 * 60 * 60;
                        } else {
                            req.settings.mute_until = 0;
                        }
                        ConnectionsManager.Instance.performRpc(req, null, null, true, RPCRequest.RPCRequestClassGeneric);
                    } else if (i == 3) {
                        Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
                        SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        Uri currentSound = null;

                        String defaultPath = null;
                        Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                        if (defaultUri != null) {
                            defaultPath = defaultUri.getPath();
                        }

                        String path = preferences.getString("sound_chat_path_" + chat_id, defaultPath);
                        if (path != null && !path.equals("NoSound")) {
                            if (path.equals(defaultPath)) {
                                currentSound = defaultUri;
                            } else {
                                currentSound = Uri.parse(path);
                            }
                        }

                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                        startActivityForResult(tmpIntent, 15);
                    } else if (i == 5) {
                        MediaActivity fragment = new MediaActivity();
                        Bundle bundle = new Bundle();
                        bundle.putLong("dialog_id", -chat_id);
                        fragment.setArguments(bundle);
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "media_chat_" + chat_id, false);
                    } else {
                        int size = 0;
                        if (info != null) {
                            size += info.participants.size();
                        }
                        if (i > 6 && i < size + 7) {
                            int user_id = info.participants.get(sortedUsers.get(i - 7)).user_id;
                            if (user_id == UserConfig.clientUserId) {
                                return;
                            }
                            UserProfileActivity fragment = new UserProfileActivity();
                            Bundle args = new Bundle();
                            args.putInt("user_id", user_id);
                            fragment.setArguments(args);
                            ((ApplicationActivity)parentActivity).presentFragment(fragment, "user_" + user_id, false);
                        } else {
                            if (size + 7 == i) {
                                if (info.participants.size() < 100) {
                                    openAddMenu();
                                } else {
                                    kickUser(null);
                                }
                            } else if (size + 7 == i + 1) {
                                kickUser(null);
                            }
                        }
                    }
                }
            });

            listView.setOnTouchListener(new OnSwipeTouchListener() {
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

    @Override
    public void didSelectContact(int user_id) {
        MessagesController.Instance.addUserToChat(chat_id, user_id, info);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        avatarUpdater.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 15) {
                Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                String name = null;
                if (ringtone != null && parentActivity != null) {
                    Ringtone rng = RingtoneManager.getRingtone(parentActivity, ringtone);
                    if (rng != null) {
                        name = rng.getTitle(parentActivity);
                        rng.stop();
                    }
                }

                SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();

                if (name != null && ringtone != null) {
                    editor.putString("sound_chat_" + chat_id, name);
                    editor.putString("sound_chat_path_" + chat_id, ringtone.toString());
                } else {
                    editor.putString("sound_chat_" + chat_id, "NoSound");
                    editor.putString("sound_chat_path_" + chat_id, "NoSound");
                }
                editor.commit();
                listView.invalidateViews();
            }
        }
    }

    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            updateOnlineCount();
            if (listView != null) {
                listView.invalidateViews();
            }
        } else if (id == MessagesController.chatInfoDidLoaded) {
            int chatId = (Integer)args[0];
            if (chatId == chat_id) {
                info = (TLRPC.ChatParticipants)args[1];
                updateOnlineCount();
                if (listViewAdapter != null) {
                    listViewAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == MessagesController.mediaCountDidLoaded) {
            long uid = (Long)args[0];
            int lower_part = (int)uid;
            if (lower_part < 0 && chat_id == -lower_part) {
                totalMediaCount = (Integer)args[1];
                if (listView != null) {
                    listView.invalidateViews();
                }
            }
        } else if (id == MessagesController.closeChats) {
            removeSelfFromStack();
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
        actionBar.setCustomView(null);
        actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.GroupInfo) + "</font>"));
        actionBar.setSubtitle(null);

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
        if (getSherlockActivity() == null) {
            return;
        }
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    WindowManager manager = (WindowManager)parentActivity.getSystemService(Activity.WINDOW_SERVICE);
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
                openAddMenu();
                break;
        }
        return true;
    }

    private void updateOnlineCount() {
        if (info == null) {
            return;
        }
        onlineCount = 0;
        int currentTime = ConnectionsManager.Instance.getCurrentTime();
        sortedUsers.clear();
        int i = 0;
        for (TLRPC.TL_chatParticipant participant : info.participants) {
            TLRPC.User user = MessagesController.Instance.users.get(participant.user_id);
            if (user != null && user.status != null && (user.status.expires > currentTime || user.status.was_online > currentTime || user.id == UserConfig.clientUserId) && (user.status.expires > 10000 || user.status.was_online > 10000)) {
                onlineCount++;
            }
            sortedUsers.add(i);
            i++;
        }

        Collections.sort(sortedUsers, new Comparator<Integer>() {
            @Override
            public int compare(Integer lhs, Integer rhs) {
                TLRPC.User user1 = MessagesController.Instance.users.get(info.participants.get(rhs).user_id);
                TLRPC.User user2 = MessagesController.Instance.users.get(info.participants.get(lhs).user_id);
                Integer status1 = 0;
                Integer status2 = 0;
                if (user1 != null && user1.status != null) {
                    if (user1.id == UserConfig.clientUserId) {
                        status1 = ConnectionsManager.Instance.getCurrentTime() + 50000;
                    } else {
                        status1 = user1.status.expires;
                        if (status1 == 0) {
                            status1 = user1.status.was_online;
                        }
                    }
                }
                if (user2 != null && user2.status != null) {
                    if (user2.id == UserConfig.clientUserId) {
                        status2 = ConnectionsManager.Instance.getCurrentTime() + 50000;
                    } else {
                        status2 = user2.status.expires;
                        if (status2 == 0) {
                            status2 = user2.status.was_online;
                        }
                    }
                }
                return status1.compareTo(status2);
            }
        });

        if (listView != null) {
            listView.invalidateViews();
        }
    }

    private void processPhotoMenu(int action) {
        if (action == 0) {
            TLRPC.Chat chat = MessagesController.Instance.chats.get(chat_id);
            if (chat.photo != null && chat.photo.photo_big != null) {
                NotificationCenter.Instance.addToMemCache(3, chat.photo.photo_big);
                Intent intent = new Intent(parentActivity, GalleryImageViewer.class);
                startActivity(intent);
            }
        } else if (action == 1) {
            avatarUpdater.openCamera();
        } else if (action == 2) {
            avatarUpdater.openGallery();
        } else if (action == 3) {
            MessagesController.Instance.changeChatAvatar(chat_id, null);
        }
    }

    private void openAddMenu() {
        ContactsActivity fragment = new ContactsActivity();
        fragment.animationType = 1;
        Bundle bundle = new Bundle();
        bundle.putBoolean("onlyUsers", true);
        bundle.putBoolean("destroyAfterSelect", true);
        bundle.putBoolean("usersAsSections", true);
        bundle.putBoolean("returnAsResult", true);
        fragment.selectAlertString = R.string.AddToTheGroup;
        fragment.delegate = this;
        if (info != null) {
            HashMap<Integer, TLRPC.User> users = new HashMap<Integer, TLRPC.User>();
            for (TLRPC.TL_chatParticipant p : info.participants) {
                users.put(p.user_id, null);
            }
            NotificationCenter.Instance.addToMemCache(7, users);
        }
        fragment.setArguments(bundle);
        ((ApplicationActivity)parentActivity).presentFragment(fragment, "contacts_block", false);
    }

    private void kickUser(TLRPC.TL_chatParticipant user) {
        if (user != null) {
            MessagesController.Instance.deleteUserFromChat(chat_id, user.user_id, info);
        } else {
            NotificationCenter.Instance.removeObserver(this, MessagesController.closeChats);
            NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
            MessagesController.Instance.deleteDialog(-chat_id, 0, false);
            MessagesController.Instance.deleteUserFromChat(chat_id, UserConfig.clientUserId, info);
            finishFragment();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.settings_block_users_bar_menu, menu);
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
            return (i == 2 || i == 3 || i == 5 || i > 6) && i != getCount() - 1;
        }

        @Override
        public int getCount() {
            int count = 6;
            if (info != null && !(info instanceof TLRPC.TL_chatParticipantsForbidden)) {
                count += info.participants.size() + 2;
                if (info.participants.size() < 100) {
                    count++;
                }
            }
            return count;
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
                BackupImageView avatarImage;
                TextView onlineText;
                TLRPC.Chat chat = MessagesController.Instance.chats.get(chat_id);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.chat_profile_avatar_layout, viewGroup, false);
                    Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                    onlineText = (TextView)view.findViewById(R.id.settings_online);
                    onlineText.setTypeface(typeface);

                    ImageButton button = (ImageButton)view.findViewById(R.id.settings_edit_name);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ChatProfileChangeNameActivity fragment = new ChatProfileChangeNameActivity();
                            Bundle bundle = new Bundle();
                            bundle.putInt("chat_id", chat_id);
                            fragment.setArguments(bundle);
                            ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_name_" + chat_id, false);
                        }
                    });

                    final ImageButton button2 = (ImageButton)view.findViewById(R.id.settings_change_avatar_button);
                    button2.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            CharSequence[] items;
                            int type;
                            TLRPC.Chat chat = MessagesController.Instance.chats.get(chat_id);
                            if (chat.photo == null || chat.photo.photo_big == null || chat.photo instanceof TLRPC.TL_chatPhotoEmpty) {
                                items = new CharSequence[] {getStringEntry(R.string.FromCamera), getStringEntry(R.string.FromGalley)};
                                type = 0;
                            } else {
                                items = new CharSequence[] {getStringEntry(R.string.OpenPhoto), getStringEntry(R.string.FromCamera), getStringEntry(R.string.FromGalley), getStringEntry(R.string.DeletePhoto)};
                                type = 1;
                            }

                            final int arg0 = type;
                            builder.setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    int action = 0;
                                    if (arg0 == 1) {
                                        if (i == 0) {
                                            action = 0;
                                        } else if (i == 1) {
                                            action = 1;
                                        } else if (i == 2) {
                                            action = 2;
                                        } else if (i == 3) {
                                            action = 3;
                                        }
                                    } else if (arg0 == 0) {
                                        if (i == 0) {
                                            action = 1;
                                        } else if (i == 1) {
                                            action = 2;
                                        }
                                    }
                                    processPhotoMenu(action);
                                }
                            });
                            builder.show().setCanceledOnTouchOutside(true);
                        }
                    });
                } else {
                    onlineText = (TextView)view.findViewById(R.id.settings_online);
                }
                avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                TextView textView = (TextView)view.findViewById(R.id.settings_name);

                textView.setText(chat.title);

                if (chat.participants_count != 0 && onlineCount > 0) {
                    onlineText.setText(Html.fromHtml(String.format("%d %s, <font color='#006fc8'>%d %s</font>", chat.participants_count, getStringEntry(R.string.Members), onlineCount, getStringEntry(R.string.Online))));
                } else {
                    onlineText.setText(String.format("%d %s", chat.participants_count, getStringEntry(R.string.Members)));
                }

                TLRPC.FileLocation photo = null;
                if (chat.photo != null) {
                    photo = chat.photo.photo_small;
                }
                avatarImage.setImage(photo, "50_50", Utilities.getGroupAvatarForId(chat.id));
                return view;
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                if (i == 1) {
                    textView.setText(getStringEntry(R.string.SETTINGS));
                } else if (i == 4) {
                    textView.setText(getStringEntry(R.string.SHAREDMEDIA));
                } else if (i == 6) {
                    TLRPC.Chat chat = MessagesController.Instance.chats.get(chat_id);
                    textView.setText(String.format("%d %s", chat.participants_count, getStringEntry(R.string.MEMBERS)));
                }
            } else if (type == 2) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_check_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == 2) {
                    SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    String key = "notify_" + (-chat_id);
                    boolean value = preferences.getBoolean(key, true);
                    ImageView checkButton = (ImageView)view.findViewById(R.id.settings_row_check_button);
                    if (value) {
                        checkButton.setImageResource(R.drawable.btn_check_on);
                    } else {
                        checkButton.setImageResource(R.drawable.btn_check_off);
                    }
                    textView.setText(getStringEntry(R.string.Notifications));
                    divider.setVisibility(View.VISIBLE);
                }
            } else if (type == 3) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_leftright_row_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                detailTextView.setTypeface(typeface);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == 3) {
                    SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    String name = preferences.getString("sound_chat_" + chat_id, getStringEntry(R.string.Default));
                    if (name.equals("NoSound")) {
                        detailTextView.setText(getStringEntry(R.string.NoSound));
                    } else {
                        detailTextView.setText(name);
                    }
                    textView.setText(R.string.Sound);
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == 5) {
                    textView.setText(R.string.SharedMedia);
                    if (totalMediaCount == -1) {
                        detailTextView.setText(getStringEntry(R.string.Loading));
                    } else {
                        detailTextView.setText(String.format("%d", totalMediaCount));
                    }
                    divider.setVisibility(View.INVISIBLE);
                }
            } else if (type == 4) {
                TLRPC.TL_chatParticipant part = info.participants.get(sortedUsers.get(i - 7));
                TLRPC.User user = MessagesController.Instance.users.get(part.user_id);

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

                if (user.first_name.length() != 0 && user.last_name.length() != 0) {
                    holder.nameTextView.setText(Html.fromHtml(user.first_name + " <b>" + user.last_name + "</b>"));
                } else if (user.first_name.length() != 0) {
                    holder.nameTextView.setText(Html.fromHtml("<b>" + user.first_name + "</b>"));
                } else {
                    holder.nameTextView.setText(Html.fromHtml("<b>" + user.last_name + "</b>"));
                }

                if (info.admin_id != UserConfig.clientUserId && part.inviter_id != UserConfig.clientUserId && part.user_id != UserConfig.clientUserId) {
                    if(android.os.Build.VERSION.SDK_INT >= 11) {
                        holder.avatarImage.setAlpha(0.7f);
                        holder.nameTextView.setAlpha(0.7f);
                        holder.messageTextView.setAlpha(0.7f);
                    }
                } else {
                    if(android.os.Build.VERSION.SDK_INT >= 11) {
                        holder.avatarImage.setAlpha(1.0f);
                        holder.nameTextView.setAlpha(1.0f);
                        holder.messageTextView.setAlpha(1.0f);
                    }
                }

                TLRPC.FileLocation photo = null;
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
                int placeHolderId = Utilities.getUserAvatarForId(user.id);
                holder.avatarImage.setImage(photo, "50_50", placeHolderId);

                if (user.status == null) {
                    holder.messageTextView.setTextColor(0xff808080);
                    holder.messageTextView.setText(getStringEntry(R.string.Offline));
                } else {
                    int currentTime = ConnectionsManager.Instance.getCurrentTime();
                    if ((user.status.expires > currentTime || user.status.was_online > currentTime || user.id == UserConfig.clientUserId) && user.status.expires != 0) {
                        holder.messageTextView.setTextColor(0xff006fc8);
                        holder.messageTextView.setText(getStringEntry(R.string.Online));
                    } else {
                        if (user.status.was_online <= 10000 && user.status.expires <= 10000) {
                            holder.messageTextView.setText(getStringEntry(R.string.Invisible));
                        } else {
                            int value = user.status.was_online;
                            if (value == 0) {
                                value = user.status.expires;
                            }
                            holder.messageTextView.setText(String.format("%s %s", getStringEntry(R.string.LastSeen), Utilities.formatDateOnline(value)));
                        }
                        holder.messageTextView.setTextColor(0xff808080);
                    }
                }
            } else if (type == 5) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.chat_profile_add_row, viewGroup, false);
                }
            } else if (type == 6) {
                if (view == null) {
                    if (view == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.settings_logout_button, viewGroup, false);
                        TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                        textView.setText(getStringEntry(R.string.DeleteAndExit));
                        textView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                                builder.setMessage(getStringEntry(R.string.AreYouSure));
                                builder.setTitle(getStringEntry(R.string.AppName));
                                builder.setPositiveButton(getStringEntry(R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        kickUser(null);
                                    }
                                });
                                builder.setNegativeButton(getStringEntry(R.string.Cancel), null);
                                builder.show().setCanceledOnTouchOutside(true);
                            }
                        });
                    }
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == 0) {
                return 0;
            } else if (i == 1 || i == 4 || i == 6) {
                return 1;
            } else if (i == 2) {
                return 2;
            } else if (i == 3 || i == 5) {
                return 3;
            } else if (i > 6) {
                int size = 0;
                if (info != null) {
                    size += info.participants.size();
                }
                if (i > 6 && i < size + 7) {
                    return 4;
                } else {
                    if (size + 7 == i) {
                        if (info != null && info.participants.size() < 100) {
                            return 5;
                        } else {
                            return 6;
                        }
                    } else if (size + 8 == i) {
                        return 6;
                    }
                }
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 7;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
