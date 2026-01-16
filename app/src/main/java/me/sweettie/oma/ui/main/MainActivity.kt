@file:Suppress("DEPRECATION")

package me.sweettie.oma.ui.main

import android.Manifest
import android.R.attr
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sweettie.oma.R
import me.sweettie.oma.data.MessageList
import me.sweettie.oma.data.Server
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.home.HomeFragment
import me.sweettie.oma.ui.home.HomeFragmentDirections
import me.sweettie.oma.ui.main.MainViewModel.UiState
import me.sweettie.oma.utils.SecurePrefs
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.SessionState
import proto.OmaOuterClass
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    lateinit var navController: NavController
    private lateinit var gestureDetector: GestureDetectorCompat
    private val swipeDistance = 300
    private val swipeVelocity = 100

    private var bannerView: View? = null
    private var bannerParams: WindowManager.LayoutParams? = null


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        SessionManager.appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
                ||
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.WAKE_LOCK
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.WAKE_LOCK
                    ), 1001
                )
            }
        }
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()


        val typedValue = TypedValue()
        val found = theme.resolveAttribute(attr.windowBackground, typedValue, true)
        val color: Int = if (found) {
            if (typedValue.resourceId != 0) {
                ContextCompat.getColor(this, typedValue.resourceId)
            } else {
                typedValue.data
            }
        } else {
            ContextCompat.getColor(this, R.color.colorSidebarBackground)
        }
        window.navigationBarColor = color

        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            FirebaseApp.initializeApp(this)
        }

        setContentView(R.layout.activity_main)

        bannerView = findViewById<LinearLayout>(R.id.topBannerRoot)


        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("OMA", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            val token = task.result
            println("New token = $token")
            CoroutineScope(Dispatchers.IO).launch {
                GrpcClient.registerFbToken(token)
            }
        })

        SessionManager.saveState(SessionState.MAIN)
        SessionManager.viewModel = ViewModelProvider(this)[MainViewModel::class]
        window?.apply {
            statusBarColor = Color.TRANSPARENT
        }
        navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onShowPress(e: MotionEvent) {}
            override fun onSingleTapUp(e: MotionEvent): Boolean = true
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean = true

            override fun onLongPress(e: MotionEvent) {}

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                println(diffX)
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > swipeDistance && abs(velocityX) > swipeVelocity) {
                        if (diffX < 0) {
                            navigateForward()
                            return true
                        }
                    }
                }
                return false
            }

        })

        handleIncomingIntent(intent)


        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val req = NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        cm.requestNetwork(req, SessionManager.networkCallback)


        lifecycleScope.launch {
            SessionManager.viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.UpdateNow -> {
                        updateLogic()
                    }

                    else -> Unit
                }
            }
        }

        SessionManager.updateFunction = ::updateLogic
        SessionManager.waitingNetworkFunction = ::waitingNetwork


    }

    fun waitingNetwork() {
        showTopBanner(getString(R.string.waiting_network)) { }
    }
    fun updateLogic() {
        hideTopBanner()
        showTopBanner(getString(R.string.fetching_update), false, {
            lifecycleScope.launch {
                println("TRY TO UPDATE....")
                val t = GrpcClient.getServers()
                if (t == null) {
                    return@launch
                }
                val servers = arrayListOf<Server>()
                println("Init servers: ${t.spacesList.size}")
                t.spacesList.forEach {
                    println("STARTUP: prepear server with id ${it.id}")
                    servers.add(Server(it))
                }
                SessionManager.servers.forEach { s ->
                    s.hashChannels.forEach {
                        SessionManager.channelMembers.remove(it.key)
                    }
                }
                SessionManager.servers = servers

                SessionManager.servers.forEach { s ->


                    s.loadAllNecessaryInfo()
                    s.members.forEach {
                        SessionManager.updateOnlineInfo(it.id, it.online)
                    }
                    s.hashChannels.forEach {


                        GrpcClient.getMessages(s.space.id, it.value.id, 0, onSuccess = { ml_ ->

                            if (SessionManager.selectedChannel != null && s.space.id == SessionManager.selectedServer!!.space.id && it.key == SessionManager.selectedChannel) {
                                val oldCopy =
                                    SessionManager.cachedMessageList[SessionManager.selectedChannel!!]!!.message_.messagesList
                                val newCopy: ArrayList<OmaOuterClass.Message> =
                                    ArrayList(ml_.messagesList)
                                newCopy.reverse()
                                for (m in ml_.messagesList) {
                                    for (mo in oldCopy) {
                                        if (mo.id.equals(m.id)) {
                                            newCopy.remove(m)
                                            break
                                        }
                                    }
                                }
                                println("NEW COPY: ${newCopy.size}")
                                for (m in newCopy) {
                                    val e = OmaOuterClass.Event.newBuilder().setMessageReceived(
                                        OmaOuterClass.MessageReceived.newBuilder()
                                            .setChannelId(SessionManager.selectedChannel!!)
                                            .setSpaceId(SessionManager.selectedServer!!.space.id)
                                            .setMessage(m)
                                    )
                                        .build()
                                    println("Build new message $m, ")
                                    SessionManager.viewModel.handleEvent(e)
                                }
                                SecurePrefs.save("last_${s.space.id}_${it.key}", newCopy.get(0).id)
//                                GrpcClient.getMessages(
//                                    SessionManager.selectedServer!!.space.id,
//                                    SessionManager.selectedChannel!!,
//                                    0,
//                                    onSuccess = {
//
//
//                                    },
//                                    onError = {
//                                        println("SOME ERROR WHILE GETTING CHAT MESSAGES")
//                                        it.printStackTrace()
//                                    })
                            }

                            // LOAD MESSAGE LIST, CHECK LAST SAVED MESSAGE, IF LAST SAVED MESSAGE ID NOT EQUALS NEW LAST MESSAGE ID - MARK HIM AS UNREAD
                            val ml = MessageList(s.space.id, it.value.id, ml_)
                            SessionManager.cachedMessageList[it.value.id] = ml
                            val lastMessage = ml.messageLis[0]
                            if (!SecurePrefs.sf!!.getString("last_${s.space.id}_${it.key}", "")
                                    .equals(lastMessage.id)
                            ) {
                                val temp = SessionManager.unreadedMessages.getOrDefault(
                                    s.space.id,
                                    arrayListOf()
                                )
                                if (!temp.contains(it.value.id)) {
                                    temp.add(it.value.id)
                                    SessionManager.unreadedMessages.put(s.space.id, temp)
                                }
                            }

                        }, onError = {})
                    }
                }
                hideTopBanner()

                withContext(Dispatchers.Main) {
                    val navHost =
                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val current = navHost?.childFragmentManager?.primaryNavigationFragment
                    if (current is HomeFragment) {
                        current.updateAllAdapters()
                    }
                }
            }
        })
    }
    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action != null) {
            if (intent.action == "START_ACTIVITY") {
                val spaceId = intent.getStringExtra("spaceID")
                val channelId = intent.getStringExtra("channelID")
                val s = SessionManager.getServerBySpaceId(spaceId!!) ?: return
                val c = s.hashChannels[channelId!!] ?: return
                SessionManager.selectedServer = s
                val action = if (c.type.equals(OmaOuterClass.ChannelType.TEXT)) {
                    HomeFragmentDirections.actionHomeToChat(
                        channelId = c.id,
                        serverId = s.space.id
                    )
                } else {
                    HomeFragmentDirections.actionHomeToVoicechat(
                        channelId = c.id,
                        serverId = s.space.id
                    )
                }
                navController.navigate(action)


            } else if (intent.action == "ACTION_OPEN_VOICE_CHAT") {
                val spaceId = intent.getStringExtra("spaceID")
                val channelId = intent.getStringExtra("channelID")
                println("OPEN VOICE CHAT WITH spaceId=$spaceId; channelID=$channelId")
                val s = SessionManager.getServerBySpaceId(spaceId!!) ?: return
                val c = s.hashChannels[channelId!!]
                if (c == null) {
                    println("Hash null, loading and re run action...")
                    lifecycleScope.launch {
                        s.loadAllNecessaryInfo()
                        handleIncomingIntent(intent)
                    }
                    return
                }
                println("Loading new view")
                SessionManager.selectedServer = s
                val action = HomeFragmentDirections.actionHomeToVoicechat(
                    channelId = c.id,
                    serverId = s.space.id,
                    join = true
                )

                navController.navigate(action)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null)
            handleIncomingIntent(intent)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    private fun navigateForward() {
        val currentDestination = navController.currentDestination ?: return
        val actionId = when (currentDestination.id) {
            R.id.homeFragment -> R.id.action_home_to_chat
            R.id.chatFragment -> R.id.action_chat_to_members
            else -> null
        }
        actionId?.let {
            if (navController.currentDestination?.getAction(it) != null) {
                navController.navigate(it)
            }
        }
    }


    fun showTopBanner(
        text: String? = null,
        touchable: Boolean = true,
        onUpdate: (() -> Unit)? = null
    ) {
        runOnUiThread {
            if (bannerView!!.visibility != View.GONE) return@runOnUiThread // уже показан


//            tv.text = message

            if (onUpdate != null) {
                onUpdate()
            }

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or if (!touchable) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.x = 0
            lp.y = 0

            try {
                bannerParams = lp
                if (text != null)
                    bannerView!!.findViewById<TextView>(R.id.bannerText).text = text
                bannerView!!.visibility = View.VISIBLE
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideTopBanner() {
        runOnUiThread {
            bannerView?.let {
                try {
                    bannerView!!.visibility = View.GONE
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    bannerParams = null
                }
            }
        }
    }

    override fun onDestroy() {
        hideTopBanner()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val lastPing = SessionManager.lastPingTime
        val now = System.currentTimeMillis()
        if (lastPing - now > 11 * 1000) {
            updateLogic()
        }
        SessionManager.lastPingTime = System.currentTimeMillis()
    }

}