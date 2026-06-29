package online.paychek.app.ui.components.plan

import online.paychek.app.data.remote.dto.PlanFeatureDto

object PlanFeaturesDefaults {

    fun subscriptionFeatures(
        maxSites: Int,
        maxDevices: Int,
        existing: List<PlanFeatureDto>? = null
    ): List<PlanFeatureDto> {
        if (!existing.isNullOrEmpty()) return existing
        return listOf(
            PlanFeatureDto("সর্বোচ্চ $maxSites টি ওয়েবসাইট সংযুক্ত করুন"),
            PlanFeatureDto("সর্বোচ্চ $maxDevices টি চাইল্ড ডিভাইস যুক্ত করুন"),
            PlanFeatureDto("২৪/৭ লাইভ এডমিন ও হোয়াটসঅ্যাপ সাপোর্ট")
        )
    }

    fun addonFeatures(
        durationDays: Int,
        description: String? = null,
        existing: List<PlanFeatureDto>? = null
    ): List<PlanFeatureDto> {
        if (!existing.isNullOrEmpty()) return existing
        val lines = mutableListOf(
            PlanFeatureDto("কাস্টম সেন্ডার আইডি যোগ করার পারমিশন"),
            PlanFeatureDto("মেয়াদ: $durationDays দিন")
        )
        description?.takeIf { it.isNotBlank() }?.let { lines.add(PlanFeatureDto(it)) }
        return lines
    }
}
