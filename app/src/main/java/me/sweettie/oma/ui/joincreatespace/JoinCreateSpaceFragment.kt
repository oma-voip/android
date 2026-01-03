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
import me.sweettie.oma.utils.getEndText
import me.sweettie.oma.utils.onTextUpdate

private enum class DoType {
    JOIN,
    CREATE,
    SEND_DIRECT
}

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
    lateinit var directMessageLinearLayout: LinearLayout


    private var doing: DoType = DoType.JOIN

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        joinSpaceLinearLayout = binding.joinSpaceLinear
        createSpaceLinearLayout = binding.createSpaceLinear
        directMessageLinearLayout = binding.openDirectLinear


        val checkButton = { s: String ->
            if (s.isEmpty() || s.isBlank()) {
                binding.joinBtn.isEnabled = false
                binding.createBtn.isEnabled = false
                binding.sendBtn.isEnabled = false
            } else {
                when (doing) {
                    DoType.JOIN -> binding.joinBtn.isEnabled = true
                    DoType.CREATE -> binding.createBtn.isEnabled = true
                    DoType.SEND_DIRECT -> binding.sendBtn.isEnabled = true
                }
            }
        }


        binding.backBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            navController.popBackStack()
        }

        val createOrJoin = View.OnClickListener {
            when (doing) {
                DoType.JOIN -> {
                    val spaceId = binding.spaceId.getEndText()
                    lifecycleScope.launch {
                        GrpcClient.joinSpace(spaceId)
                        binding.backBtn.performClick()
                    }
                }

                DoType.CREATE -> {
                    val spaceName = binding.spaceName.getEndText()
                    lifecycleScope.launch {
                        GrpcClient.createSpace(spaceName)
                        binding.backBtn.performClick()
                    }
                }

                DoType.SEND_DIRECT -> {
                    val userId = binding.directId.getEndText()
                    lifecycleScope.launch {
                        GrpcClient.createPrivateDialog(userId)
                        binding.backBtn.performClick()
                    }
                }
            }
        }



        binding.joinPrompt.setOnClickListener(makeChangeListener(DoType.JOIN))
        binding.joinPrompt2.setOnClickListener(makeChangeListener(DoType.JOIN))
        binding.createPrompt.setOnClickListener(makeChangeListener(DoType.CREATE))
        binding.createSpace.setOnClickListener(makeChangeListener(DoType.CREATE))
        binding.createPrivateDialog.setOnClickListener(makeChangeListener(DoType.SEND_DIRECT))
        binding.createPrivateDialog2.setOnClickListener(makeChangeListener(DoType.SEND_DIRECT))

        binding.spaceId.onTextUpdate(checkButton)
        binding.spaceName.onTextUpdate(checkButton)
        binding.directId.onTextUpdate(checkButton)

        binding.joinBtn.setOnClickListener(createOrJoin)
        binding.createBtn.setOnClickListener(createOrJoin)
        binding.sendBtn.setOnClickListener(createOrJoin)


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


    private fun makeChangeListener(type: DoType): View.OnClickListener {
        return View.OnClickListener {
            doing = type
            when (doing) {
                DoType.JOIN -> {
                    createSpaceLinearLayout.visibility = View.GONE
                    directMessageLinearLayout.visibility = View.GONE
                    //                binding.spaceName.clear()
                    joinSpaceLinearLayout.visibility = View.VISIBLE
                    binding.spaceId.requestFocus()
                }

                DoType.CREATE -> {
                    joinSpaceLinearLayout.visibility = View.GONE
                    directMessageLinearLayout.visibility = View.GONE
                    //                binding.spaceId.clear()
                    createSpaceLinearLayout.visibility = View.VISIBLE
                    binding.spaceName.requestFocus()
                }

                DoType.SEND_DIRECT -> {
                    joinSpaceLinearLayout.visibility = View.GONE
                    createSpaceLinearLayout.visibility = View.GONE
                    //                binding.spaceId.clear()
                    directMessageLinearLayout.visibility = View.VISIBLE
                    binding.directId.requestFocus()

                }
            }
        }
    }

}