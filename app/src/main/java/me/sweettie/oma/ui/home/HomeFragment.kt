package me.sweettie.oma.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.sweettie.oma.data.Server
import me.sweettie.oma.databinding.FragmentHomeBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import proto.OmaOuterClass

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel




    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        println("server size: ${SessionManager.servers.size}")
        binding.rvServers.layoutManager = LinearLayoutManager(requireContext())

        val serverClickCallback: (Server) -> Unit = { server: Server ->
            println("Выбран сервер: ${server.space.name}")


            binding.channelMenu.visibility = View.VISIBLE
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
                (binding.serverChannels.adapter as ChannelAdapter).updateServer(server)
                Utils.animateTextChange(binding.serverName, server.space.name)
            }


        }



        binding.serverChannels.layoutManager = LinearLayoutManager(requireContext())
        binding.serverChannels.adapter = ChannelAdapter(null) { s, c ->
            val navController =
                (activity as? MainActivity)?.navController ?: return@ChannelAdapter
            var action: NavDirections
            if (s.space.private) {
                binding.channelMenu.visibility = View.VISIBLE
                lifecycleScope.launch {
                    if (SessionManager.selectedServer == null) {
                        s.loadAllNecessaryInfo()
                        SessionManager.selectedServer = s
                    } else if (SessionManager.selectedServer!!.space.id != s.space.id) {
                        s.loadAllNecessaryInfo()
                        SessionManager.selectedChannel = null
                        SessionManager.selectedServer = s

                    }
                    s.members.forEach {
                        SessionManager.updateOnlineInfo(it.id, it.online)
                    }
                    action =
                        HomeFragmentDirections.actionHomeToChat(
                            channelId = s.channels.first().id,
                            serverId = s.space.id
                        )
                    navController.navigate(action)

                }
            } else {
                println("выбран канал: ${c.name}")
                action = if (c.type.equals(OmaOuterClass.ChannelType.TEXT)) {
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

            }
        }


        val privateCallback = {
            binding.channelMenu.visibility = View.VISIBLE
            (binding.serverChannels.adapter as ChannelAdapter).updatePrivateDialogs(
                SessionManager.servers.filter { server -> server.space.private }.toList()
            )
            Utils.animateTextChange(binding.serverName, "Private dialogs")
        }
        binding.rvServers.adapter = ServerAdapter(
            serverClickCallback,
            onPrivateListClick = privateCallback, onAddClick = {
                val navController =
                    (activity as? MainActivity)?.navController ?: return@ServerAdapter
                val action = HomeFragmentDirections.actionMainToJoincreateSpace()
                navController.navigate(action)
            })

        (binding.rvServers.adapter as ServerAdapter).prepareItems(SessionManager.servers)

        if ((binding.rvServers.adapter as ServerAdapter).itemCount >= 1 && SessionManager.selectedServer == null) {
            serverClickCallback(SessionManager.servers.getFirstNotPrivate())
        } else if (SessionManager.selectedServer != null) {
            if (SessionManager.selectedServer!!.space.private) {
                privateCallback()
                (binding.rvServers.adapter as ServerAdapter).selectedSpaceId = "private"
                (binding.rvServers.adapter as ServerAdapter).notifyItemChanged(0)
                (binding.rvServers.adapter as ServerAdapter).notifyItemChanged(1)
            } else {
                (binding.rvServers.adapter as ServerAdapter).selectedSpaceId =
                    SessionManager.selectedServer!!.space.id
                serverClickCallback(SessionManager.selectedServer!!)
            }
        }


        reg()


    }

    fun reg() {

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                println("STATE IN HOME FRAGMENT!")
                when (state) {

                    is MainViewModel.UiState.ServerJoined -> {
                        if (SessionManager.getServerBySpaceId(state.spaceId) == null) {
                            val r = GrpcClient.getSpace(state.spaceId) ?: return@collect
                            SessionManager.servers.add(Server(r.space))
                            (binding.rvServers.adapter as ServerAdapter).prepareItems(
                                SessionManager.servers
                            )
                        } else {
                            SessionManager.getServerBySpaceId(state.spaceId)!!
                                .loadAllNecessaryInfo()
                        }
                    }

                    is MainViewModel.UiState.ChannelCreated -> {
                        val r = SessionManager.getServerBySpaceId(state.channel.spaceId)
                            ?: return@collect
                        if (SessionManager.selectedServer == null)
                            return@collect
                        if (SessionManager.selectedServer!!.space.id.equals(r.space.id)) {
                            println("Try to update server channels!")
                            lifecycleScope.launch {
                                r.loadAllNecessaryInfo()
                                SessionManager.updateServer(r)
                                (binding.serverChannels.adapter as ChannelAdapter).updateServer(
                                    r
                                )
                            }
                        }

                    }

                    is MainViewModel.UiState.ChannelMembers -> {
                        if (SessionManager.selectedServer != null) {
                            val tempServersIndexed =
                                SessionManager.selectedServer!!.channels.sortedWith(
                                    compareBy<OmaOuterClass.Channel> { it.type != OmaOuterClass.ChannelType.TEXT }
                                        .thenBy { it.name.lowercase() }
                                )
                            val index =
                                tempServersIndexed.indexOf(SessionManager.selectedServer!!.hashChannels[state.channelMembers.channelId])
                            (binding.serverChannels.adapter as ChannelAdapter).notifyItemChanged(
                                index
                            )
                        }
                    }

                    is MainViewModel.UiState.ChannelJoined -> {
                        if (SessionManager.selectedServer != null) {
                            val tempServersIndexed =
                                SessionManager.selectedServer!!.channels.sortedWith(
                                    compareBy<OmaOuterClass.Channel> { it.type != OmaOuterClass.ChannelType.TEXT }
                                        .thenBy { it.name.lowercase() }
                                )
                            val index =
                                tempServersIndexed.indexOf(SessionManager.selectedServer!!.hashChannels[state.channel.channelId])
                            (binding.serverChannels.adapter as ChannelAdapter).notifyItemChanged(
                                index
                            )
                        }
                    }

                    is MainViewModel.UiState.ChannelLeft -> {
                        if (SessionManager.selectedServer != null) {
                            val tempServersIndexed =
                                SessionManager.selectedServer!!.channels.sortedWith(
                                    compareBy<OmaOuterClass.Channel> { it.type != OmaOuterClass.ChannelType.TEXT }
                                        .thenBy { it.name.lowercase() }
                                )
                            val index =
                                tempServersIndexed.indexOf(SessionManager.selectedServer!!.hashChannels[state.channel.channelId])
                            (binding.serverChannels.adapter as ChannelAdapter).notifyItemChanged(
                                index
                            )
                        }
                    }


                    else -> Unit
                }

            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}