package com.openclassrooms.tourguide.dto;

import lombok.Getter;

@Getter
public class NearbyAttractionDTO {
    private String attractionName;
    private double attractionLatitude;
    private double attractionLongitude;
    private double userLatitude;
    private double userLongitude;
    private double distanceMiles;
    private int rewardPoints;

    public NearbyAttractionDTO(String attractionName,
                               double attractionLatitude,
                               double attractionLongitude,
                               double userLatitude,
                               double userLongitude,
                               double distanceMiles,
                               int rewardPoints) {
        this.attractionName = attractionName;
        this.attractionLatitude = attractionLatitude;
        this.attractionLongitude = attractionLongitude;
        this.userLatitude = userLatitude;
        this.userLongitude = userLongitude;
        this.distanceMiles = distanceMiles;
        this.rewardPoints = rewardPoints;
    }

    // --- Getters & Setters ---

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public void setAttractionLatitude(double attractionLatitude) {
        this.attractionLatitude = attractionLatitude;
    }

    public void setAttractionLongitude(double attractionLongitude) {
        this.attractionLongitude = attractionLongitude;
    }

    public void setUserLatitude(double userLatitude) {
        this.userLatitude = userLatitude;
    }

    public void setUserLongitude(double userLongitude) {
        this.userLongitude = userLongitude;
    }

    public void setDistanceMiles(double distanceMiles) {
        this.distanceMiles = distanceMiles;
    }

    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }
}
