package com.versionone.apiclient.cycle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.versionone.apiclient.APIException;
import com.versionone.apiclient.AndFilterTerm;
import com.versionone.apiclient.Asset;
import com.versionone.apiclient.ConnectionException;
import com.versionone.apiclient.FilterTerm;
import com.versionone.apiclient.IAssetType;
import com.versionone.apiclient.IAttributeDefinition;
import com.versionone.apiclient.IFilterTerm;
import com.versionone.apiclient.IMetaModel;
import com.versionone.apiclient.IServices;
import com.versionone.apiclient.OidException;
import com.versionone.apiclient.Query;
import com.versionone.apiclient.QueryResult;

public class AssetSelector {
    private HashMap<String, IAttributeDefinition> attributeDefinitions;
    private Query query;
    IFilterTerm filterTerm;

    public AssetSelector(IMetaModel metaModel, String assetTypeName, String... attributes) {
        attributeDefinitions = new HashMap<String, IAttributeDefinition>(attributes.length);
        IAssetType assetType = metaModel.getAssetType(assetTypeName);
        query = new Query(assetType);
        for (String attribute : attributes) {
            IAttributeDefinition attributeDefinition = assetType.getAttributeDefinition(attribute);
            query.getSelection().add(attributeDefinition);
            attributeDefinitions.put(attribute, attributeDefinition);
        }
    }

    public IAttributeDefinition attribute(String name) {
        return attributeDefinitions.get(name);
    }

    public void filterAttributeEquals(String attributeName, Object... value) {
        if (filterTerm == null) {
            FilterTerm term = new FilterTerm(attribute(attributeName));
            term.equal(value);
            filterTerm = term;
        } else {
            FilterTerm term = new FilterTerm(attribute(attributeName));
            term.equal(value);
            filterTerm = new AndFilterTerm(filterTerm, term);
            
        }
        query.setFilter(filterTerm);
    }

   public List<AssetResult> retrieve(IServices services) throws Exception {
        QueryResult result = services.retrieve(query);
        ArrayList<AssetResult> assetResults = new ArrayList<AssetResult>(result.getAssets().length);
        for (Asset asset : result.getAssets()) {
            assetResults.add(new AssetResult(this, asset));
        }
        return assetResults;
    }
}
