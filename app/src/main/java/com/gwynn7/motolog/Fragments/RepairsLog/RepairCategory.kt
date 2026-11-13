package com.gwynn7.motolog.Fragments.RepairsLog

import com.gwynn7.motolog.R

/** Indices match published records; labels remain in the existing localized array. */
fun repairCategoryIcon(index: Int): Int = when (index) {
    0 -> R.drawable.ic_service_engine
    1 -> R.drawable.ic_service_chain
    2 -> R.drawable.ic_tire
    3 -> R.drawable.ic_service_brakes
    4 -> R.drawable.ic_service_fork
    5 -> R.drawable.ic_service_documents
    7 -> R.drawable.ic_service_fuel
    else -> R.drawable.ic_repair
}
