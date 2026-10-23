plugins {
    alias(libs.plugins.android.asset.pack)
}

// Delivered by Google Play, so users never visit Hugging Face or accept a licence themselves.
// On-demand (not fast-follow) so phones too small for the AI helper never download 530 MB:
// the app asks Play for it on first start only when the phone can run it.
assetPack {
    packName.set("gemma_pack")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
