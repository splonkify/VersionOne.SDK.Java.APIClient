package com.versionone.apiclient.cycle;

import java.util.Comparator;
import java.util.Date;

public class HistoryComparator implements Comparator<AssetResult> {

    @Override
    public int compare(AssetResult arg0, AssetResult arg1) {
        // TODO Auto-generated method stub
        try {
            return ((Date) arg0.value("ChangeDate")).compareTo((Date) arg1.value("ChangeDate"));
        } catch (Exception e) {
            return 0;
        }
    }

}
