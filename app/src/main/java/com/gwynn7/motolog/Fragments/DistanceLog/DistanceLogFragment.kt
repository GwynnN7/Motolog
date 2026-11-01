package com.gwynn7.motolog.Fragments.DistanceLog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.DistancelogListBinding
import com.gwynn7.motolog.stop

class DistanceLogFragment : Fragment() {
    private var _binding: DistancelogListBinding? = null
    private val binding get() = _binding!!
    private val motorcycleViewModel: MotorcycleViewModel by activityViewModels()
    private lateinit var adapter: DistanceLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DistancelogListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bikeId = motorcycleViewModel.selectedBikeId ?: run {
            stop(activity)
            return
        }

        adapter = DistanceLogAdapter(startKm = 0) { log ->
            findNavController().navigate(
                DistanceLogFragmentDirections.distancelogToDistanceadd(bikeId, log.id)
            )
        }
        binding.rwDistancelogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rwDistancelogs.adapter = adapter
        binding.fabAddDistanceLog.setOnClickListener {
            findNavController().navigate(DistanceLogFragmentDirections.distancelogToDistanceadd(bikeId))
        }

        motorcycleViewModel.getMotorcycle(bikeId).observe(viewLifecycleOwner) { bike ->
            if (bike == null) stop(activity) else adapter.updateStartKm(bike.start_km)
        }
        motorcycleViewModel.getDistanceLogs(bikeId).observe(viewLifecycleOwner) { logs ->
            binding.placeholder.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(logs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
