package me.sweettie.oma.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.sweettie.oma.data.Server
import me.sweettie.oma.databinding.FragmentHomeBinding
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils

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


        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        println("server size: ${SessionManager.servers.size}")
        binding.rvServers.layoutManager = LinearLayoutManager(requireContext())

        val serverClickCallback: (Server) -> Unit = { server: Server ->
            println("Выбран сервер: ${server.space.name}")
//            binding.serverName.text = server.space.name


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
                (binding.serverChannels.adapter as ChannelAdapter).updateServer(server)
                Utils.animateTextChange(binding.serverName, server.space.name)
            }


        }



        binding.serverChannels.layoutManager = LinearLayoutManager(requireContext())
        binding.serverChannels.adapter = ChannelAdapter(null) { s, c ->
            println("выбран канал: ${c.name}")

            val navController = (activity as? MainActivity)?.navController ?: return@ChannelAdapter
            val action = HomeFragmentDirections.actionHomeToChat(
                channelId = c.id,
                serverId = s.space.id
            )
            navController.navigate(action)
        }


        binding.rvServers.adapter = ServerAdapter(serverClickCallback, onAddClick = {

        })
        (binding.rvServers.adapter as ServerAdapter).prepareItems(SessionManager.servers)

        if ((binding.rvServers.adapter as ServerAdapter).itemCount >= 1 && SessionManager.selectedServer == null) {
            serverClickCallback(SessionManager.servers[0])
        } else if (SessionManager.selectedServer != null) {
            serverClickCallback(SessionManager.selectedServer!!)
        }


    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

//    override fun onResume() {
//        super.onResume()
//        (activity as? AppCompatActivity)?.supportActionBar?.hide()
//    }
//
//    override fun onPause() {
//        super.onPause()
//        (activity as? AppCompatActivity)?.supportActionBar?.show()
//    }

}