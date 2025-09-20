package me.sweettie.oma.ui.joincreatespace

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.sweettie.oma.data.Server
import me.sweettie.oma.databinding.FragmentJoincreatespaceBinding

class JoinCreateSpaceFragment : Fragment() {


    private var _binding: FragmentJoincreatespaceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJoincreatespaceBinding.inflate(inflater, container, false)
        return binding.root
    }


    lateinit var space: Server

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        space = SessionManager.getServerBySpaceId(args.serverId)!!
//        binding.memberListActionBarText.text =
//            "\"${space.hashChannels[args.channelId]!!.name}\" members"
//
//        binding.memberListAdapter.layoutManager = LinearLayoutManager(requireContext())
//        binding.memberListAdapter.adapter = MemberListAdapter()
//        println("Members size: ${space.members.size}")
//        (binding.memberListAdapter.adapter as MemberListAdapter).updateData(space.members)
//        binding.backBtn.setOnClickListener {
//            val navController =
//                (activity as? MainActivity)?.navController ?: return@setOnClickListener
//            navController.popBackStack()
//        }
    }

}