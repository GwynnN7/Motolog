package com.gwynn7.motolog.Fragments.ModsLog

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
import com.gwynn7.motolog.databinding.ModslogListBinding
import com.gwynn7.motolog.stop

class ModsLogFragment : Fragment() {
    private var _binding: ModslogListBinding? = null
    private val binding get() = _binding!!
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private lateinit var currentBike: Motorcycle

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModslogListBinding.inflate(inflater, container, false)

        binding.fabAddModsLog.setOnClickListener {
            val action = ModsLogFragmentDirections.modslogToModsadd(currentBike)
            findNavController().navigate(action)
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val adapter = ModsLogAdapter()
        binding.rwModslogs.adapter = adapter
        binding.rwModslogs.layoutManager = LinearLayoutManager(requireContext())

        val bikeId = MotorcycleViewModel.currentBikeId
        if (bikeId == null) {
            stop(activity)
            return
        }

        val bikeData = mMotorcycleViewModel.getMotorcycle(bikeId)
        bikeData.observe(viewLifecycleOwner) { bikes ->
            if (bikes.isNotEmpty()) {
                currentBike = bikes.first()
                binding.placeholder.visibility = if (currentBike.logs.mods.isEmpty()) View.VISIBLE else View.GONE
                adapter.bindBike(currentBike)
            } else stop(activity)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
