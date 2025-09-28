package me.sweettie.oma.ui.joincreatespace

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.data.Server
import me.sweettie.oma.databinding.FragmentJoincreatespaceBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.utils.clear
import me.sweettie.oma.utils.getEndText
import me.sweettie.oma.utils.onTextUpdate

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

    lateinit var joinSpaceLinearLayout: LinearLayout
    lateinit var createSpaceLinearLayout: LinearLayout

    private var joinBoolean = true

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        joinSpaceLinearLayout = binding.joinSpaceLinear
        createSpaceLinearLayout = binding.createSpaceLinear

        val changeLinear = View.OnClickListener {
            joinBoolean = !joinBoolean
            if (joinBoolean) {
                createSpaceLinearLayout.visibility = View.GONE
                binding.spaceName.clear()
                joinSpaceLinearLayout.visibility = View.VISIBLE
            } else {
                createSpaceLinearLayout.visibility = View.VISIBLE
                binding.spaceId.clear()
                joinSpaceLinearLayout.visibility = View.GONE
            }
        }

        val checkButton = { s: String ->
            if (s.isEmpty() || s.isBlank()) {
                binding.joinBtn.isEnabled = false
                binding.createBtn.isEnabled = false
            } else {
                if (joinBoolean)
                    binding.joinBtn.isEnabled = true
                else
                    binding.createBtn.isEnabled = true
            }
        }

        binding.backBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            navController.popBackStack()
        }

        val createOrJoin = View.OnClickListener {
            if (joinBoolean) {
                val spaceId = binding.spaceId.getEndText()
                lifecycleScope.launch {
                    GrpcClient.joinSpace(spaceId)
                    binding.backBtn.performClick()
                }
            } else {
                val spaceName = binding.spaceName.getEndText()
                lifecycleScope.launch {
                    GrpcClient.createSpace(spaceName)
                    binding.backBtn.performClick()
                }
            }
        }



        binding.joinPrompt.setOnClickListener(changeLinear)
        binding.createPrompt.setOnClickListener(changeLinear)

        binding.spaceId.onTextUpdate(checkButton)
        binding.spaceName.onTextUpdate(checkButton)

        binding.joinBtn.setOnClickListener(createOrJoin)


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