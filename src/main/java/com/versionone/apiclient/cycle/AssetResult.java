package com.versionone.apiclient.cycle;

import com.versionone.apiclient.Asset;

public class AssetResult {
    private AssetSelector selector;
    private Asset asset;
    
    public AssetResult(AssetSelector selector, Asset asset) {
        this.selector = selector;
        this.asset = asset;
    }
    
    public Object value(String attributeName) throws Exception {
        return asset.getAttribute(selector.attribute(attributeName)).getValue();
    }

    public Object[] values(String attributeName) throws Exception {
        return asset.getAttribute(selector.attribute(attributeName)).getValues();
    }

    public String getId() {
        return asset.getOid().getToken();
    }

    public Asset getAsset() {
        return asset;
    }

}
