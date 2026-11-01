package com.gwynn7.motolog.Fragments.ModsLog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.ModslogListBinding
import com.gwynn7.motolog.stop

class ModsLogFragment : Fragment() {
    private var _binding: ModslogListBinding? = null
    private val binding get() = _binding!!
    private val motorcycleViewModel: MotorcycleViewModel by activityViewModels()
    private lateinit var adapter: ModsLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = ModslogListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bikeId = motorcycleViewModel.selectedBikeId ?: run {
            stop(activity)
            return
        }

        adapter = ModsLogAdapter { log ->
            findNavController().navigate(ModsLogFragmentDirections.modslogToModsadd(bikeId, log.id))
        }
        binding.rwModslogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rwModslogs.adapter = adapter
        binding.fabAddModsLog.setOnClickListener {
            findNavController().navigate(ModsLogFragmentDirections.modslogToModsadd(bikeId))
        }

        motorcycleViewModel.getMotorcycle(bikeId).observe(viewLifecycleOwner) { bike ->
            if (bike == null) stop(activity)
        }
        motorcycleViewModel.getModsLogs(bikeId).observe(viewLifecycleOwner) { logs ->
            binding.placeholder.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(logs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
