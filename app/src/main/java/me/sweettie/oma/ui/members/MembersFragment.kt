package me.sweettie.oma.ui.members

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import me.sweettie.oma.data.Server
import me.sweettie.oma.databinding.FragmentMembersBinding
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
        binding.memberListAdapter.adapter = MemberListAdapter()
        println("Members size: ${space.members.size}")
        (binding.memberListAdapter.adapter as MemberListAdapter).updateData(space.members)
        binding.backBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            navController.popBackStack()
        }
    }

}