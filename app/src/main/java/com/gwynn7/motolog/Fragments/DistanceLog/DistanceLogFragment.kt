package com.gwynn7.motolog.Fragments.DistanceLog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.DistancelogListBinding
import com.gwynn7.motolog.stop

class DistanceLogFragment : Fragment() {
    private var _binding: DistancelogListBinding? = null
    private val binding get() = _binding!!
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private lateinit var currentBike: Motorcycle

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DistancelogListBinding.inflate(inflater, container, false)

        binding.fabAddDistanceLog.setOnClickListener {
            val action = DistanceLogFragmentDirections.distancelogToDistanceadd(currentBike)
            findNavController().navigate(action)
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val list = binding.rwDistancelogs
        list.layoutManager = LinearLayoutManager(requireContext())

        val bikeId = MotorcycleViewModel.currentBikeId
        if (bikeId == null) {
            stop(activity)
            return
        }

        val bikeData = mMotorcycleViewModel.getMotorcycle(bikeId)
        bikeData.observe(viewLifecycleOwner) { bikes ->
            if (bikes.isNotEmpty()) {
                currentBike = bikes.first()
                binding.placeholder.visibility = if (currentBike.logs.distance.isEmpty()) View.VISIBLE else View.GONE

                val adapter = DistanceLogAdapter(currentBike)
                list.adapter = adapter
                adapter.submitList(currentBike.logs.distance)
            } else stop(activity)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
