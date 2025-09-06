package me.sweettie.oma.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.sweettie.oma.databinding.FragmentHomeBinding
import me.sweettie.oma.utils.SessionManager

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
        binding.rvServers.adapter = ServerAdapter { server ->
            println("Выбран сервер: ${server.space.name}")
            binding.serverName.text = server.space.name
            binding.channelMenu.visibility = View.VISIBLE
            lifecycleScope.launch {
                server.loadAllNecessaryInfo()
                withCreated {
                    binding.serverChannels.layoutManager = LinearLayoutManager(requireContext())
                    binding.serverChannels.adapter = ChannelAdapter(server) {
                        println("выбран канал: ${it.name}")
                    }
                }
            }
        }


    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
    }

    override fun onPause() {
        super.onPause()
        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }

}