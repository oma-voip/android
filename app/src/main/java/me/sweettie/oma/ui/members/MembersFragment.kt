package me.sweettie.oma.ui.members

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.sweettie.oma.data.Server
import me.sweettie.oma.databinding.FragmentMembersBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.utils.SessionManager


class MembersFragment : Fragment() {


    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val args: MembersFragmentArgs by navArgs()

    lateinit var space: Server

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        space = SessionManager.getServerBySpaceId(args.serverId)!!
        binding.memberListActionBarText.text =
            "\"${space.hashChannels[args.channelId]!!.name}\" members"

        binding.memberListAdapter.layoutManager = LinearLayoutManager(requireContext())
        binding.memberListAdapter.adapter = MemberListAdapter({ user, view ->
            val popup = PopupMenu(view.context, view)
            popup.menu.add(0, 1, 0, "Copy ID")
            popup.menu.add(0, 2, 1, "Send Direct")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        val clipboard: ClipboardManager =
                            view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("User ID", user.id)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(
                            this@MembersFragment.requireContext(),
                            "User ID copied!",
                            Toast.LENGTH_LONG
                        ).show()
                        true
                    }

                    2 -> {
                        var server = SessionManager.getServerById(user.id)
                        val navController =
                            (activity as? MainActivity)?.navController
                                ?: return@setOnMenuItemClickListener false
                        var action: NavDirections
                        if (server != null) {
                            lifecycleScope.launch {
                                if (SessionManager.selectedServer == null) {
                                    server.loadAllNecessaryInfo()
                                    SessionManager.selectedServer = server
                                } else if (SessionManager.selectedServer!!.space.id != server.space.id) {
                                    server.loadAllNecessaryInfo()
                                    SessionManager.selectedChannel = null
                                    SessionManager.selectedServer = server

                                }
                                server.members.forEach {
                                    SessionManager.updateOnlineInfo(it.id, it.online)
                                }
                                action =
                                    MembersFragmentDirections.memberToChat(
                                        channelId = server.channels.first().id,
                                        serverId = server.space.id
                                    )
                                navController.navigate(action)
                            }
                        } else {
                            lifecycleScope.launch {
                                val response = GrpcClient.createPrivateDialog(user.id)
                                if (response == null)
                                    return@launch
                                server = Server(response.space)
                                SessionManager.servers.add(server)
                                if (SessionManager.selectedServer == null) {
                                    server.loadAllNecessaryInfo()
                                    SessionManager.selectedServer = server
                                } else if (SessionManager.selectedServer!!.space.id != server.space.id) {
                                    server.loadAllNecessaryInfo()
                                    SessionManager.selectedChannel = null
                                    SessionManager.selectedServer = server

                                }
                                server.members.forEach {
                                    SessionManager.updateOnlineInfo(it.id, it.online)
                                }
                                action =
                                    MembersFragmentDirections.memberToChat(
                                        channelId = server.channels.first().id,
                                        serverId = server.space.id
                                    )
                                navController.navigate(action)
                            }
                        }
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        })
        println("Members size: ${space.members.size}")
        (binding.memberListAdapter.adapter as MemberListAdapter).updateData(space.members)
        binding.backBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            navController.popBackStack()
        }
    }

}