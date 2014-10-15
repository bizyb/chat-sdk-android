package com.braunster.chatsdk.fragments.abstracted;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.TimingLogger;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.braunster.chatsdk.R;
import com.braunster.chatsdk.Utils.Debug;
import com.braunster.chatsdk.activities.abstracted.ChatSDKAbstractChatActivity;
import com.braunster.chatsdk.adapter.AbstractThreadsListAdapter;
import com.braunster.chatsdk.adapter.ThreadsListAdapter;
import com.braunster.chatsdk.dao.BMessage;
import com.braunster.chatsdk.dao.BThread;
import com.braunster.chatsdk.dao.core.DaoCore;
import com.braunster.chatsdk.dao.entities.Entity;
import com.braunster.chatsdk.fragments.ChatSDKBaseFragment;
import com.braunster.chatsdk.network.BNetworkManager;
import com.braunster.chatsdk.network.events.BatchedEvent;
import com.braunster.chatsdk.network.events.Event;
import com.braunster.chatsdk.network.firebase.EventManager;
import com.braunster.chatsdk.object.Batcher;
import com.braunster.chatsdk.object.ChatSDKThreadPool;
import com.braunster.chatsdk.object.UIUpdater;

import java.util.List;

/**
 * Created by itzik on 6/17/2014.
 */
public class ChatSDKAbstractConversationsFragment extends ChatSDKBaseFragment {

    private static final String TAG = ChatSDKAbstractConversationsFragment.class.getSimpleName();
    private static boolean DEBUG = Debug.ConversationsFragment;
    public static final String APP_EVENT_TAG= "ConverstaionFragment";

    protected ListView listThreads;
    protected AbstractThreadsListAdapter adapter;
    protected ProgressBar progressBar;

    protected TimingLogger timings;
    protected UIUpdater uiUpdater;

    protected boolean inflateMenuItems = true;

    protected AdapterView.OnItemLongClickListener onItemLongClickListener;
    protected AdapterView.OnItemClickListener onItemClickListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().registerReceiver(receiver, new IntentFilter(ChatSDKAbstractChatActivity.ACTION_CHAT_CLOSED));
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void initViews() {
        listThreads = (ListView) mainView.findViewById(R.id.list_threads);
        progressBar = (ProgressBar) mainView.findViewById(R.id.chat_sdk_progress_bar);
        initList();
    }

    private void initList(){

        // Create the adpater only if null, This is here so we wont override the adapter given from the extended class with setAdapter.
        if (adapter == null)
            adapter = new ThreadsListAdapter(getActivity());

        listThreads.setAdapter(adapter);

        if (onItemClickListener==null)
        {
            onItemClickListener = new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (DEBUG) Log.i(TAG, "Thread Selected: " + adapter.getItem(position).getName()
                            + ", ID: " + adapter.getItem(position).getEntityId() );
                    startChatActivityForID(adapter.getItem(position).getId());
                }
            };
        }

        listThreads.setOnItemClickListener(onItemClickListener);

        if (onItemLongClickListener== null)
        {
            onItemLongClickListener = new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    if (DEBUG)
                        Log.i(TAG, "Thread Long Selected: " + adapter.getItem(position).getName()
                                + ", ID: " + adapter.getItem(position).getEntityId());

                    showAlertDialog("", getResources().getString(R.string.alert_delete_thread), getResources().getString(R.string.delete),
                            getResources().getString(R.string.cancel), null, new DeleteThread(adapter.getItem(position).getEntityId()));

                    return true;
                }
            };
        }

        listThreads.setOnItemLongClickListener(onItemLongClickListener);
    }

    @Override
    public void loadData() {
        super.loadData();

        if (mainView == null)
            return;

        List<BThread> threads = BNetworkManager.sharedManager().getNetworkAdapter().threadsWithType(BThread.Type.Private);

        adapter.setThreadItems(adapter.makeList(threads));

        if (DEBUG) Log.d(TAG, "Threads, Amount: " + (threads != null ? threads.size(): "No Threads") );
    }

    @Override
    public void loadDataOnBackground() {
        super.loadDataOnBackground();

        if (DEBUG) Log.d(TAG, "isLoggable: " + Log.isLoggable(TAG.substring(0, 21), Log.VERBOSE));

        if (DEBUG) timings = new TimingLogger(TAG.substring(0, 21), "loadDataOnBackground");

        if (DEBUG) Log.v(TAG.substring(0, 21), "loadDataOnBackground");

        if (mainView == null)
        {
            if (DEBUG) Log.e(TAG.substring(0, 21), "Main view is null");
            return;
        }

        final boolean isFirst;
        if (uiUpdater != null)
        {
            isFirst = false;
            uiUpdater.setKilled(true);
            ChatSDKThreadPool.getInstance().removeSchedule(uiUpdater);
        }
        else
        {
            isFirst = true;
        }

        final boolean hasItems = adapter != null && adapter.getThreadItems().size() > 0;

        if (isFirst && !hasItems) {
            if (DEBUG) Log.v(TAG, "loadDataOnBackground, hiding list.");
            listThreads.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.VISIBLE);
        }

        uiUpdater = new UIUpdater() {
            @Override
            public void run() {
                if (DEBUG) Log.d(TAG, "Run, " + isFirst + ", " + hasItems + ", " + isKilled());

                if (isKilled() && !isFirst && hasItems)
                {
                    if (DEBUG) Log.v(TAG, "uiUpdater, is killed.");
                    return;

                }

                if (DEBUG) {
                    timings.addSplit("Loading threads");
                }

                List<ThreadsListAdapter.ThreadListItem> list = BNetworkManager.sharedManager().getNetworkAdapter().threadItemsWithType(BThread.Type.Private, adapter.getItemMaker());

                if (DEBUG) {
                    Log.d(TAG, "Thread Loaded");
                    timings.addSplit("Loading threads");
                }

                uiUpdater = null;

                Message message = new Message();
                message.obj = list;
                message.what = 1;
                handler.sendMessage(message);

                if (DEBUG) timings.addSplit("Sending message to handler.");
            }
        };

        ChatSDKThreadPool.getInstance().scheduleExecute(uiUpdater,isFirst ? 1 : 4);
    }

    @Override
    public void refreshForEntity(Entity entity) {
        super.refreshForEntity(entity);
        if (adapter.getCount() == 0)
            return;;

        adapter.replaceOrAddItem((BThread) entity);
        if (progressBar.getVisibility() == View.VISIBLE)
        {
            progressBar.setVisibility(View.GONE);
            listThreads.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void clearData() {
        if (adapter != null)
        {
            if (uiUpdater != null)
                uiUpdater.setKilled(true);

            adapter.getThreadItems().clear();
            adapter.notifyDataSetChanged();
        }
    }

    private Handler handler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what)
            {
                case 1:
                    if (DEBUG) Log.d(TAG, "UpdaeUI" + ((List<ThreadsListAdapter.ThreadListItem>) msg.obj).size());
                    adapter.setThreadItems((List<ThreadsListAdapter.ThreadListItem>) msg.obj);
                    if (progressBar.getVisibility() == View.VISIBLE)
                    {
                        progressBar.setVisibility(View.INVISIBLE);
                        listThreads.setVisibility(View.VISIBLE);
                    }
                    if (DEBUG) {
                        timings.addSplit("Updating UI");
                        timings.dumpToLog();
                        timings.reset(TAG.substring(0, 21), "loadDataOnBackground");
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!inflateMenuItems)
            return;

        super.onCreateOptionsMenu(menu, inflater);
        MenuItem item =
                menu.add(Menu.NONE, R.id.action_chat_sdk_add, 10, "Add Conversation");
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        item.setIcon(R.drawable.ic_plus);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){

        if (!inflateMenuItems)
            return super.onOptionsItemSelected(item);

        /* Cant use switch in the library*/
        int id = item.getItemId();

        if (id == R.id.action_chat_sdk_add)
        {
            startPickFriendsActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();

//        loadDataOnBackground();

        BatchedEvent batchedEvents = new BatchedEvent(APP_EVENT_TAG, "", Event.Type.AppEvent, handler);

        batchedEvents.setBatchedAction(Event.Type.MessageEvent, 1000, new Batcher.BatchedAction<String>() {
            @Override
            public void triggered(List<String> list) {
                if (DEBUG) Log.v(TAG, "onMessageReceived");
                for (String messageID : list)
                {
                    BMessage message = DaoCore.fetchEntityWithEntityID(BMessage.class, messageID);
                    if (message.getBThreadOwner().getType() == BThread.Type.Private)
                    {
                        loadDataOnBackground();
                        return;
                    }
                }
            }
        });

        batchedEvents.setBatchedAction(Event.Type.ThreadEvent, 3000, new Batcher.BatchedAction<String>() {
            @Override
            public void triggered(List<String> list) {
                if (DEBUG) Log.v(TAG, "onThreadDetailsChanged");
                for (String threadId : list)
                {
                    BThread thread = DaoCore.<BThread>fetchEntityWithEntityID(BThread.class, threadId);
                    if (thread.getType() != null && thread.getType() == BThread.Type.Private)
                    {
                        loadDataOnBackground();
                        return;
                    }
                }
            }
        });

        batchedEvents.setBatchedAction(Event.Type.UserDetailsEvent, 2500, new Batcher.BatchedAction<String>() {
            @Override
            public void triggered(List<String> list) {
                if (DEBUG) Log.v(TAG, "onUserDetailsChange");
                loadDataOnBackground();
            }
        });

        EventManager.getInstance().removeEventByTag(APP_EVENT_TAG);
        EventManager.getInstance().addAppEvent(batchedEvents);
    }

    public void setAdapter(AbstractThreadsListAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            getActivity().unregisterReceiver(receiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ChatSDKAbstractChatActivity.ACTION_CHAT_CLOSED))
            {
                loadDataOnBackground();
            }
        }
    };

    public void filterThreads(String text){
        adapter.filterItems(text);
    }

    public AbstractThreadsListAdapter getAdapter() {
        return adapter;
    }
}
