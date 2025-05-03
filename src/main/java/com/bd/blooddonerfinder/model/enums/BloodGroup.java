package com.bd.blooddonerfinder.model.enums;

import lombok.Getter;

@Getter
public enum BloodGroup {
    A_POSITIVE("A+", new String[]{"A+", "AB+"}),
    A_NEGATIVE("A-", new String[]{"A+", "A-", "AB+", "AB-"}),
    B_POSITIVE("B+", new String[]{"B+", "AB+"}),
    B_NEGATIVE("B-", new String[]{"B+", "B-", "AB+", "AB-"}),
    AB_POSITIVE("AB+", new String[]{"AB+"}),
    AB_NEGATIVE("AB-", new String[]{"AB+", "AB-"}),
    O_POSITIVE("O+", new String[]{"A+", "B+", "AB+", "O+"}),
    O_NEGATIVE("O-", new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"});

    private final String displayName;
    private final  String[] compatibleWith;

    BloodGroup(String displayName, String[] compatibleWith) {
        this.displayName = displayName;
        this.compatibleWith = compatibleWith;
    }

    public boolean isCompatibleWith(BloodGroup otherBloodGroup){
        for(String group : compatibleWith){
            if(group.equals(otherBloodGroup.displayName)){
                return  true;
            }
        }
        return  false;
    }

    public static BloodGroup fromDisplayName(String displayName){
        for(BloodGroup bloodGroup : BloodGroup.values()){
            if(bloodGroup.displayName.equalsIgnoreCase(displayName)){
                return bloodGroup;
            }
        }
        return null;
    }
}
